package com.collegeconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.collegeconnect.dto.AbsenceExplanationRequest;
import com.collegeconnect.dto.AbsenceReviewRequest;
import com.collegeconnect.dto.DocumentScanResultRequest;
import com.collegeconnect.dto.LeaveRequestCreateRequest;
import com.collegeconnect.dto.LeaveReviewRequest;
import com.collegeconnect.entity.AuditLog;
import com.collegeconnect.entity.NotificationOutbox;
import com.collegeconnect.repository.AuditLogRepository;
import com.collegeconnect.repository.NotificationOutboxRepository;
import com.collegeconnect.security.Role;
import com.collegeconnect.service.ParentWorkflowService;
import com.collegeconnect.service.SupportingDocumentService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
class PhaseSevenWorkflowIntegrationTest extends IntegrationTestSupport {

    private static final Path STORAGE_DIRECTORY =
            Path.of(System.getProperty("java.io.tmpdir"), "collegeconnect-phase7-" + UUID.randomUUID());

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SupportingDocumentService supportingDocumentService;

    @Autowired
    private ParentWorkflowService parentWorkflowService;

    @MockitoSpyBean
    private AuditLogRepository auditLogRepository;

    @MockitoSpyBean
    private NotificationOutboxRepository notificationOutboxRepository;

    @DynamicPropertySource
    static void registerDocumentStorage(DynamicPropertyRegistry registry) {
        registry.add("collegeconnect.documents.storage-directory", STORAGE_DIRECTORY::toString);
        registry.add("collegeconnect.documents.max-size-bytes", () -> 5_242_880L);
    }

    @AfterAll
    static void removeTestStorage() throws IOException {
        if (Files.exists(STORAGE_DIRECTORY)) {
            try (var paths = Files.walk(STORAGE_DIRECTORY)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    @Test
    void documentUploadScanDownloadAndHistoryUsePrivateStorageAndAudit() {
        Fixture fixture = createFixture();
        var uploaded = supportingDocumentService.uploadForAbsence(
                fixture.parentAccountId(),
                fixture.absenceCaseId(),
                pdf("medical-proof.pdf"));

        assertThat(uploaded.scanStatus()).isEqualTo("PENDING_SCAN");
        assertThatThrownBy(() -> supportingDocumentService.download(
                        fixture.parentAccountId(), Role.PARENT, uploaded.documentId()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode().value()).isEqualTo(409));

        supportingDocumentService.recordScanResult(
                fixture.adminAccountId(),
                uploaded.documentId(),
                new DocumentScanResultRequest("CLEAN", "External scan completed"));

        byte[] downloaded = supportingDocumentService.download(
                fixture.parentAccountId(), Role.PARENT, uploaded.documentId());
        assertThat(downloaded).containsExactly(pdfBytes());
        assertThat(supportingDocumentService.listForAbsence(
                        fixture.professorAccountId(), Role.PROFESSOR, fixture.absenceCaseId()))
                .extracting(response -> response.documentId())
                .containsExactly(uploaded.documentId());

        List<String> actions = jdbcTemplate.queryForList(
                "select action from audit_log where entity_type = 'SUPPORTING_DOCUMENT' "
                        + "and entity_id = ? order by created_at",
                String.class,
                uploaded.documentId().toString());
        assertThat(actions).contains("DOCUMENT_UPLOADED", "DOCUMENT_SCAN_CLEAN", "DOCUMENT_DOWNLOADED");
    }

    @Test
    void documentAccessRequiresLinkedParentOrAssignedProfessor() {
        Fixture fixture = createFixture();
        var uploaded = supportingDocumentService.uploadForAbsence(
                fixture.parentAccountId(),
                fixture.absenceCaseId(),
                pdf("medical-proof.pdf"));
        long otherParentId = jdbcTemplate.queryForObject(
                "select id from parent_profile where account_id = ?",
                Long.class,
                fixture.unlinkedParentAccountId());
        jdbcTemplate.update(
                "insert into parent_student_link(parent_id, student_id, verification_status, activated_at) "
                        + "values (?, ?, ?, ?)",
                otherParentId,
                studentIdFor(fixture.absenceCaseId()),
                "VERIFIED",
                java.sql.Timestamp.from(Instant.now()));

        assertThatThrownBy(() -> supportingDocumentService.getMetadata(
                        fixture.unlinkedParentAccountId(), Role.PARENT, uploaded.documentId()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode().value()).isEqualTo(403));
        assertThat(supportingDocumentService.listForAbsence(
                        fixture.unlinkedParentAccountId(), Role.PARENT, fixture.absenceCaseId()))
                .isEmpty();
        assertThatThrownBy(() -> supportingDocumentService.getMetadata(
                        fixture.unassignedProfessorAccountId(), Role.PROFESSOR, uploaded.documentId()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode().value()).isEqualTo(403));
        assertThat(supportingDocumentService.getMetadata(
                        fixture.professorAccountId(), Role.PROFESSOR, uploaded.documentId()).documentId())
                .isEqualTo(uploaded.documentId());
    }

    @Test
    void leaveDocumentsAreAvailableOnlyToLinkedParentAssignedProfessorAndAdmin() {
        Fixture fixture = createFixture();
        var uploaded = supportingDocumentService.uploadForLeave(
                fixture.parentAccountId(),
                fixture.leaveRequestId(),
                pdf("leave-proof.pdf"));

        assertThat(supportingDocumentService.listForLeave(
                        fixture.parentAccountId(), Role.PARENT, fixture.leaveRequestId()))
                .extracting(response -> response.documentId())
                .containsExactly(uploaded.documentId());
        assertThat(supportingDocumentService.listForLeave(
                        fixture.professorAccountId(), Role.PROFESSOR, fixture.leaveRequestId()))
                .extracting(response -> response.documentId())
                .containsExactly(uploaded.documentId());
        assertThat(supportingDocumentService.listForLeave(
                        fixture.adminAccountId(), Role.ADMIN, fixture.leaveRequestId()))
                .extracting(response -> response.documentId())
                .containsExactly(uploaded.documentId());
    }

    @Test
    void replacementAndDeletionAreAuditedAndRemoveSupersededPrivateFiles() {
        Fixture fixture = createFixture();
        var original = supportingDocumentService.uploadForAbsence(
                fixture.parentAccountId(),
                fixture.absenceCaseId(),
                pdf("medical-proof.pdf"));
        String originalKey = storageKey(original.documentId());
        Path originalPath = STORAGE_DIRECTORY.resolve(originalKey);

        var replacement = supportingDocumentService.replace(
                fixture.parentAccountId(), original.documentId(), png("replacement.png"));

        assertThat(jdbcTemplate.queryForObject(
                        "select scan_status from supporting_document where id = ?",
                        String.class,
                        original.documentId()))
                .isEqualTo("REPLACED");
        assertThat(Files.exists(originalPath)).isFalse();
        assertThat(replacement.scanStatus()).isEqualTo("PENDING_SCAN");

        String replacementKey = storageKey(replacement.documentId());
        Path replacementPath = STORAGE_DIRECTORY.resolve(replacementKey);
        assertThat(Files.exists(replacementPath)).isTrue();
        supportingDocumentService.delete(fixture.parentAccountId(), replacement.documentId());

        assertThat(jdbcTemplate.queryForObject(
                        "select scan_status from supporting_document where id = ?",
                        String.class,
                        replacement.documentId()))
                .isEqualTo("DELETED");
        assertThat(Files.exists(replacementPath)).isFalse();
        assertThat(actions(original.documentId())).contains("DOCUMENT_REPLACED");
        assertThat(actions(replacement.documentId()))
                .contains("DOCUMENT_UPLOADED", "DOCUMENT_DELETED");
    }

    @Test
    void unsupportedContentAndOversizedDocumentsAreRejected() {
        Fixture fixture = createFixture();
        MockMultipartFile executable = new MockMultipartFile(
                "file", "proof.exe", "application/x-msdownload", new byte[] {0x4D, 0x5A});
        assertThatThrownBy(() -> supportingDocumentService.uploadForAbsence(
                        fixture.parentAccountId(), fixture.absenceCaseId(), executable))
                .isInstanceOf(ResponseStatusException.class);

        MockMultipartFile oversized = new MockMultipartFile(
                "file", "large.pdf", "application/pdf", new byte[5_242_881]);
        assertThatThrownBy(() -> supportingDocumentService.uploadForAbsence(
                        fixture.parentAccountId(), fixture.absenceCaseId(), oversized))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(count(
                        "select count(*) from supporting_document where absence_case_id = ?",
                        fixture.absenceCaseId()))
                .isZero();
        assertThat(count(
                        "select count(*) from audit_log where entity_type = 'SUPPORTING_DOCUMENT' "
                                + "and actor_account_id = ?",
                        fixture.parentAccountId()))
                .isZero();
    }

    @Test
    void documentUploadAndAuditRollbackTogetherWhenAuditPersistenceFails() throws IOException {
        Fixture fixture = createFixture();
        Files.createDirectories(STORAGE_DIRECTORY);
        Set<String> existingFiles;
        try (var files = Files.list(STORAGE_DIRECTORY)) {
            existingFiles = files.map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet());
        }
        doThrow(new IllegalStateException("Simulated audit persistence failure"))
                .when(auditLogRepository)
                .save(any(AuditLog.class));

        assertThatThrownBy(() -> supportingDocumentService.uploadForAbsence(
                        fixture.parentAccountId(),
                        fixture.absenceCaseId(),
                        pdf("medical-proof.pdf")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(count(
                        "select count(*) from supporting_document where absence_case_id = ?",
                        fixture.absenceCaseId()))
                .isZero();
        assertThat(actionsForActor(fixture.parentAccountId())).isEmpty();
        try (var files = Files.list(STORAGE_DIRECTORY)) {
            assertThat(files.map(path -> path.getFileName().toString())
                            .collect(java.util.stream.Collectors.toSet()))
                    .isEqualTo(existingFiles);
        }
    }

    @Test
    void parentExplanationAndAdminReviewDoNotRewriteAttendance() {
        Fixture fixture = createFixture();

        var explained = parentWorkflowService.explainAbsence(
                fixture.parentAccountId(),
                fixture.absenceCaseId(),
                new AbsenceExplanationRequest("Medical appointment"));

        assertThat(explained.status()).isEqualTo("UNDER_REVIEW");
        assertThat(jdbcTemplate.queryForObject(
                        "select status from attendance_record where id = ?",
                        String.class,
                        fixture.attendanceId()))
                .isEqualTo("ABSENT");

        var reviewed = parentWorkflowService.reviewAbsence(
                fixture.adminAccountId(),
                fixture.absenceCaseId(),
                new AbsenceReviewRequest("ACCEPTED", "Reviewed"));

        assertThat(reviewed.status()).isEqualTo("ACCEPTED");
        assertThat(jdbcTemplate.queryForObject(
                        "select status from attendance_record where id = ?",
                        String.class,
                        fixture.attendanceId()))
                .isEqualTo("ABSENT");
        assertThat(count("select count(*) from notification_outbox where source_type = 'ABSENCE_DECISION' "
                + "and source_id = ?", fixture.absenceCaseId())).isEqualTo(1);
    }

    @Test
    void parentCanCreateViewAndCancelOwnUnreviewedLeave() {
        Fixture fixture = createFixture();
        var created = parentWorkflowService.createLeave(
                fixture.parentAccountId(),
                new LeaveRequestCreateRequest(
                        studentIdFor(fixture.absenceCaseId()),
                        LocalDate.now().plusDays(4),
                        LocalDate.now().plusDays(5),
                        "MEDICAL",
                        "Medical appointment"));

        assertThat(created.status()).isEqualTo("SUBMITTED");
        assertThat(parentWorkflowService.listParentLeave(fixture.parentAccountId()))
                .extracting(response -> response.requestId())
                .contains(created.requestId());
        assertThat(parentWorkflowService.cancelLeave(fixture.parentAccountId(), created.requestId()).status())
                .isEqualTo("CANCELLED");
    }

    @Test
    void parentCanRequestSameDayLeaveBeforeTheScheduledSessionStarts() {
        Fixture fixture = createFixture();
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        LocalTime startTime = now.plusMinutes(10);
        if (startTime.isBefore(now)) {
            startTime = LocalTime.MAX.minusMinutes(1);
        }
        LocalTime endTime = startTime.isAfter(LocalTime.of(23, 29))
                ? LocalTime.MAX
                : startTime.plusMinutes(30);
        addSession(fixture, today, startTime, endTime);

        var response = parentWorkflowService.createLeave(
                fixture.parentAccountId(),
                new LeaveRequestCreateRequest(
                        studentIdFor(fixture.absenceCaseId()),
                        today,
                        today,
                        "MEDICAL",
                        "Medical appointment"));

        assertThat(response.status()).isEqualTo("SUBMITTED");
    }

    @Test
    void parentCannotRequestLeaveAfterAnAffectedSessionHasStarted() {
        Fixture fixture = createFixture();
        LocalDate today = LocalDate.now();
        addSession(
                fixture,
                today,
                LocalTime.MIN,
                LocalTime.of(1, 0));

        assertThatThrownBy(() -> parentWorkflowService.createLeave(
                        fixture.parentAccountId(),
                        new LeaveRequestCreateRequest(
                                studentIdFor(fixture.absenceCaseId()),
                                today,
                                today,
                                "MEDICAL",
                                "Medical appointment")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void adminLeaveDecisionCreatesNotificationIntentAtomically() {
        Fixture fixture = createFixture();

        var response = parentWorkflowService.reviewLeave(
                fixture.adminAccountId(),
                fixture.leaveRequestId(),
                new LeaveReviewRequest("APPROVED", "Approved for testing"));

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(count(
                        "select count(*) from notification_outbox where source_type = 'LEAVE_DECISION' "
                                + "and source_id = ?",
                        fixture.leaveRequestId()))
                .isEqualTo(1);
        assertThat(scalar(
                        "select status from notification_outbox where source_type = 'LEAVE_DECISION' "
                                + "and source_id = ?",
                        fixture.leaveRequestId()))
                .isEqualTo("SKIPPED_NO_OPTIN");
    }

    @Test
    void leaveCanEnterReviewWithoutNotifyingUntilDecisionIsFinal() {
        Fixture fixture = createFixture();

        var response = parentWorkflowService.reviewLeave(
                fixture.adminAccountId(),
                fixture.leaveRequestId(),
                new LeaveReviewRequest("UNDER_REVIEW", "Review started"));

        assertThat(response.status()).isEqualTo("UNDER_REVIEW");
        assertThat(count(
                        "select count(*) from notification_outbox where source_type = 'LEAVE_DECISION' "
                                + "and source_id = ?",
                        fixture.leaveRequestId()))
                .isZero();
        assertThat(parentWorkflowService.cancelLeave(
                        fixture.parentAccountId(), fixture.leaveRequestId()).status())
                .isEqualTo("CANCELLED");
    }

    @Test
    void leaveDecisionAndNotificationIntentRollbackTogetherOnOutboxFailure() {
        Fixture fixture = createFixture();
        doThrow(new IllegalStateException("Simulated notification persistence failure"))
                .when(notificationOutboxRepository)
                .save(any(NotificationOutbox.class));

        assertThatThrownBy(() -> parentWorkflowService.reviewLeave(
                        fixture.adminAccountId(),
                        fixture.leaveRequestId(),
                        new LeaveReviewRequest("APPROVED", "Approved")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(scalar(
                        "select status from leave_request where id = ?",
                        fixture.leaveRequestId()))
                .isEqualTo("SUBMITTED");
        assertThat(count(
                        "select count(*) from notification_outbox where source_type = 'LEAVE_DECISION' "
                                + "and source_id = ?",
                        fixture.leaveRequestId()))
                .isZero();
    }

    private Fixture createFixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        long departmentId = insert("insert into department(name) values (?)", "Department-" + suffix);
        long programmeId = insert(
                "insert into programme(department_id, name) values (?, ?)", departmentId, "Programme-" + suffix);
        LocalDate yearStart = LocalDate.of(2025, 1, 1);
        LocalDate yearEnd = LocalDate.of(2027, 12, 31);
        long yearId = insert(
                "insert into academic_year(name, start_date, end_date) values (?, ?, ?)",
                "Year-" + suffix,
                Date.valueOf(yearStart),
                Date.valueOf(yearEnd));
        long semesterId = insert(
                "insert into semester(academic_year_id, name, start_date, end_date) values (?, ?, ?, ?)",
                yearId,
                "Sem-" + suffix.substring(0, 20),
                Date.valueOf(yearStart),
                Date.valueOf(yearEnd));
        long sectionId = insert(
                "insert into section(programme_id, semester_id, name) values (?, ?, ?)",
                programmeId,
                semesterId,
                "Section-" + suffix);
        long subjectId = insert(
                "insert into subject(code, name) values (?, ?)", "SUB-" + suffix, "Subject-" + suffix);
        long offeringId = insert(
                "insert into subject_offering(subject_id, section_id, semester_id, status) values (?, ?, ?, ?)",
                subjectId,
                sectionId,
                semesterId,
                "ACTIVE");
        long adminAccountId = account("admin-" + suffix, "ADMIN");
        long parentAccountId = account("parent-" + suffix, "PARENT");
        long unlinkedParentAccountId = account("other-parent-" + suffix, "PARENT");
        long professorAccountId = account("professor-" + suffix, "PROFESSOR");
        long unassignedProfessorAccountId = account("other-professor-" + suffix, "PROFESSOR");
        long studentAccountId = account("student-" + suffix, "STUDENT");
        long parentId = insert(
                "insert into parent_profile(account_id, display_name) values (?, ?)",
                parentAccountId,
                "Parent-" + suffix);
        insert(
                "insert into parent_profile(account_id, display_name) values (?, ?)",
                unlinkedParentAccountId,
                "Other Parent-" + suffix);
        long professorId = insert(
                "insert into professor_profile(account_id, employee_identifier, department_id, display_name) "
                        + "values (?, ?, ?, ?)",
                professorAccountId,
                "EMP-" + suffix,
                departmentId,
                "Professor-" + suffix);
        insert(
                "insert into professor_profile(account_id, employee_identifier, department_id, display_name) "
                        + "values (?, ?, ?, ?)",
                unassignedProfessorAccountId,
                "OTHER-EMP-" + suffix,
                departmentId,
                "Other Professor-" + suffix);
        long studentId = insert(
                "insert into student_profile(account_id, college_identifier, programme_id, section_id, "
                        + "display_name, status) values (?, ?, ?, ?, ?, ?)",
                studentAccountId,
                "COL-" + suffix,
                programmeId,
                sectionId,
                "Student-" + suffix,
                "ACTIVE");
        jdbcTemplate.update(
                "insert into parent_student_link(parent_id, student_id, verification_status, activated_at) "
                        + "values (?, ?, ?, ?)",
                parentId,
                studentId,
                "VERIFIED",
                java.sql.Timestamp.from(Instant.now()));
        long assignmentId = insert(
                "insert into professor_assignment(professor_id, offering_id, status) values (?, ?, ?)",
                professorId,
                offeringId,
                "ACTIVE");
        LocalDate sessionDate = LocalDate.now().minusDays(1);
        long sessionId = insert(
                "insert into class_session(assignment_id, session_date, start_time, end_time, status) "
                        + "values (?, ?, ?, ?, ?)",
                assignmentId,
                Date.valueOf(sessionDate),
                java.sql.Time.valueOf(LocalTime.of(9, 0)),
                java.sql.Time.valueOf(LocalTime.of(10, 0)),
                "SUBMITTED");
        jdbcTemplate.update(
                "insert into session_roster(session_id, student_id) values (?, ?)", sessionId, studentId);
        long attendanceId = insert(
                "insert into attendance_record(session_id, student_id, submitted_by_professor_id, status, "
                        + "version, submitted_at, source_action) values (?, ?, ?, ?, ?, ?, ?)",
                sessionId,
                studentId,
                professorId,
                "ABSENT",
                0L,
                java.sql.Timestamp.from(Instant.now()),
                "PROFESSOR_SUBMIT");
        long absenceCaseId = insert(
                "insert into absence_case(attendance_id, status, created_at, updated_at) values (?, ?, ?, ?)",
                attendanceId,
                "UNEXPLAINED",
                java.sql.Timestamp.from(Instant.now()),
                java.sql.Timestamp.from(Instant.now()));
        long leaveRequestId = insert(
                "insert into leave_request(student_id, requester_account_id, start_date, end_date, "
                        + "reason_category, reason, status, submitted_at) values (?, ?, ?, ?, ?, ?, ?, ?)",
                studentId,
                parentAccountId,
                Date.valueOf(sessionDate),
                Date.valueOf(sessionDate),
                "MEDICAL",
                "Test leave",
                "SUBMITTED",
                java.sql.Timestamp.from(Instant.now()));
        return new Fixture(
                parentAccountId,
                unlinkedParentAccountId,
                adminAccountId,
                professorAccountId,
                unassignedProfessorAccountId,
                attendanceId,
                absenceCaseId,
                leaveRequestId);
    }

    private long account(String identifier, String role) {
        return insert(
                "insert into user_account(login_identifier, password_hash, role, status) values (?, ?, ?, ?)",
                identifier,
                "test-hash",
                role,
                "ACTIVE");
    }

    private void addSession(Fixture fixture, LocalDate sessionDate, LocalTime startTime, LocalTime endTime) {
        Long assignmentId = jdbcTemplate.queryForObject(
                "select assignment_id from class_session where id = "
                        + "(select session_id from attendance_record where id = ?)",
                Long.class,
                fixture.attendanceId());
        insert(
                "insert into class_session(assignment_id, session_date, start_time, end_time, status) "
                        + "values (?, ?, ?, ?, ?)",
                assignmentId,
                Date.valueOf(sessionDate),
                java.sql.Time.valueOf(startTime),
                java.sql.Time.valueOf(endTime),
                "SCHEDULED");
    }

    private long insert(String sql, Object... parameters) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Database did not return a generated key");
        }
        return key.longValue();
    }

    private int count(String sql, Object... parameters) {
        return jdbcTemplate.queryForObject(sql, Integer.class, parameters);
    }

    private String scalar(String sql, Object... parameters) {
        return jdbcTemplate.queryForObject(sql, String.class, parameters);
    }

    private Long studentIdFor(Long absenceCaseId) {
        return jdbcTemplate.queryForObject(
                "select student_id from attendance_record where id = "
                        + "(select attendance_id from absence_case where id = ?)",
                Long.class,
                absenceCaseId);
    }

    private String storageKey(Long documentId) {
        return jdbcTemplate.queryForObject(
                "select private_storage_key from supporting_document where id = ?",
                String.class,
                documentId);
    }

    private List<String> actions(Long documentId) {
        return jdbcTemplate.queryForList(
                "select action from audit_log where entity_type = 'SUPPORTING_DOCUMENT' "
                        + "and entity_id = ? order by created_at",
                String.class,
                documentId.toString());
    }

    private List<String> actionsForActor(Long accountId) {
        return jdbcTemplate.queryForList(
                "select action from audit_log where entity_type = 'SUPPORTING_DOCUMENT' "
                        + "and actor_account_id = ?",
                String.class,
                accountId);
    }

    private MockMultipartFile pdf(String fileName) {
        return new MockMultipartFile("file", fileName, "application/pdf", pdfBytes());
    }

    private MockMultipartFile png(String fileName) {
        return new MockMultipartFile(
                "file",
                fileName,
                "image/png",
                new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1});
    }

    private byte[] pdfBytes() {
        return new byte[] {'%', 'P', 'D', 'F', '-', '1', '.', '7'};
    }

    private record Fixture(
            Long parentAccountId,
            Long unlinkedParentAccountId,
            Long adminAccountId,
            Long professorAccountId,
            Long unassignedProfessorAccountId,
            Long attendanceId,
            Long absenceCaseId,
            Long leaveRequestId) {}
}

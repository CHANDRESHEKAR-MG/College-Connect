package com.collegeconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.collegeconnect.dto.AttendanceMarkRequest;
import com.collegeconnect.dto.AttendanceCorrectionRequest;
import com.collegeconnect.dto.AttendanceSubmissionRequest;
import com.collegeconnect.entity.NotificationOutbox;
import com.collegeconnect.repository.NotificationOutboxRepository;
import com.collegeconnect.security.Role;
import com.collegeconnect.service.AttendanceCorrectionService;
import com.collegeconnect.service.AttendanceSubmissionService;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
class AttendanceCorrectionTransactionIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AttendanceCorrectionService attendanceCorrectionService;

    @Autowired
    private AttendanceSubmissionService attendanceSubmissionService;

    @MockitoSpyBean
    private NotificationOutboxRepository notificationOutboxRepository;

    @Test
    void concurrentCorrectionsWithSameVersionCommitOnlyOneConsistentResult() throws Exception {
        Fixture fixture = createFixture(true, "SUBMITTED", true, true, false);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CorrectionResult> first = executor.submit(
                    () -> correctConcurrently(fixture, ready, start));
            Future<CorrectionResult> second = executor.submit(
                    () -> correctConcurrently(fixture, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<CorrectionResult> results = List.of(
                    first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS));
            assertThat(results.stream().filter(CorrectionResult::succeeded).count()).isEqualTo(1);
            assertThat(results.stream().filter(result -> result.conflict() != null).count()).isEqualTo(1);
            assertThat(results.stream().filter(result -> result.conflict() != null)
                    .findFirst().orElseThrow().conflict().getStatusCode().value())
                    .isEqualTo(409);

            assertThat(count("select count(*) from attendance_correction where attendance_id = ?",
                    fixture.attendanceId())).isEqualTo(1);
            assertThat(scalar("select status from attendance_record where id = ?", fixture.attendanceId()))
                    .isEqualTo("PRESENT");
            assertThat(scalar("select status from absence_case where attendance_id = ?", fixture.attendanceId()))
                    .isEqualTo("CLOSED_CORRECTED");
            assertThat(scalar("select status from notification_outbox where source_type = 'ATTENDANCE' "
                    + "and source_id = ?", fixture.attendanceId())).isEqualTo("CANCELLED");
            assertThat(scalar("select status from notification_outbox where source_type = 'ATTENDANCE_CORRECTION' "
                    + "and payload_json like ?", "%\"attendanceId\":" + fixture.attendanceId() + "%"))
                    .isEqualTo("SKIPPED_NO_OPTIN");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void approvedLeaveCorrectionUsesRealDatabaseAndCommitsCorrectionHistory() {
        Fixture fixture = createFixture(true, "SUBMITTED", true, true, true);

        var response = attendanceCorrectionService.correct(
                fixture.attendanceId(),
                fixture.adminAccountId(),
                Role.ADMIN,
                new AttendanceCorrectionRequest(0L, "AUTHORIZED_LEAVE", "Approved leave", "Verified"));

        assertThat(response.previousStatus()).isEqualTo("ABSENT");
        assertThat(response.newStatus()).isEqualTo("AUTHORIZED_LEAVE");
        assertThat(count("select count(*) from attendance_correction where attendance_id = ?",
                fixture.attendanceId())).isEqualTo(1);
        assertThat(scalar("select status from attendance_record where id = ?", fixture.attendanceId()))
                .isEqualTo("AUTHORIZED_LEAVE");
        assertThat(scalar("select status from absence_case where attendance_id = ?", fixture.attendanceId()))
                .isEqualTo("CLOSED_CORRECTED");
        assertThat(scalar("select status from notification_outbox where source_type = 'ATTENDANCE' "
                + "and source_id = ?", fixture.attendanceId())).isEqualTo("CANCELLED");
        assertThat(scalar("select status from notification_outbox where source_type = 'ATTENDANCE_CORRECTION' "
                + "and payload_json like ?", "%\"attendanceId\":" + fixture.attendanceId() + "%"))
                .isEqualTo("SKIPPED_NO_OPTIN");
    }

    @Test
    void correctionWithoutApprovedLeaveDoesNotChangeAttendanceOrHistory() {
        Fixture fixture = createFixture(true, "SUBMITTED", true, true, false);

        assertThatThrownBy(() -> attendanceCorrectionService.correct(
                        fixture.attendanceId(),
                        fixture.adminAccountId(),
                        Role.ADMIN,
                        new AttendanceCorrectionRequest(0L, "AUTHORIZED_LEAVE", "Leave proof", null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode().value())
                        .isEqualTo(400));

        assertThat(scalar("select status from attendance_record where id = ?", fixture.attendanceId()))
                .isEqualTo("ABSENT");
        assertThat(count("select count(*) from attendance_correction where attendance_id = ?",
                fixture.attendanceId())).isZero();
        assertThat(scalar("select status from absence_case where attendance_id = ?", fixture.attendanceId()))
                .isEqualTo("UNEXPLAINED");
    }

    @Test
    void correctionPersistenceFailureRollsBackAttendanceHistoryCaseAndOutboxChanges() {
        Fixture fixture = createFixture(true, "SUBMITTED", true, true, false);
        doThrow(new IllegalStateException("Simulated correction notification persistence failure"))
                .when(notificationOutboxRepository)
                .save(any(NotificationOutbox.class));

        assertThatThrownBy(() -> attendanceCorrectionService.correct(
                        fixture.attendanceId(),
                        fixture.adminAccountId(),
                        Role.ADMIN,
                        new AttendanceCorrectionRequest(0L, "PRESENT", "Incorrect absence", null)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(scalar("select status from attendance_record where id = ?", fixture.attendanceId()))
                .isEqualTo("ABSENT");
        assertThat(count("select count(*) from attendance_correction where attendance_id = ?",
                fixture.attendanceId())).isZero();
        assertThat(scalar("select status from absence_case where attendance_id = ?", fixture.attendanceId()))
                .isEqualTo("UNEXPLAINED");
        assertThat(scalar("select status from notification_outbox where source_type = 'ATTENDANCE' "
                + "and source_id = ?", fixture.attendanceId())).isEqualTo("PENDING");
        assertThat(count("select count(*) from notification_outbox where source_type = 'ATTENDANCE_CORRECTION' "
                + "and payload_json like ?", "%\"attendanceId\":" + fixture.attendanceId() + "%")).isZero();
    }

    @Test
    void attendanceSubmissionPersistenceFailureRollsBackAttendanceAndAbsenceCase() {
        Fixture fixture = createFixture(false, "SCHEDULED", false, false, false);
        doThrow(new IllegalStateException("Simulated outbox persistence failure"))
                .when(notificationOutboxRepository)
                .saveAll(any());

        assertThatThrownBy(() -> attendanceSubmissionService.submit(
                        fixture.sessionId(),
                        fixture.professorAccountId(),
                        Role.PROFESSOR,
                        new AttendanceSubmissionRequest(
                                List.of(new AttendanceMarkRequest(fixture.studentId(), "ABSENT")))))
                .isInstanceOf(IllegalStateException.class);

        assertThat(count("select count(*) from attendance_record where session_id = ?", fixture.sessionId()))
                .isZero();
        assertThat(count("select count(*) from absence_case where attendance_id in "
                + "(select id from attendance_record where session_id = ?)", fixture.sessionId())).isZero();
        assertThat(count("select count(*) from notification_outbox where source_type = 'ATTENDANCE' "
                + "and source_id in (select id from attendance_record where session_id = ?)",
                fixture.sessionId())).isZero();
        assertThat(scalar("select status from class_session where id = ?", fixture.sessionId()))
                .isEqualTo("SCHEDULED");
    }

    private CorrectionResult correctConcurrently(
            Fixture fixture, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent correction test did not start");
        }
        try {
            attendanceCorrectionService.correct(
                    fixture.attendanceId(),
                    fixture.adminAccountId(),
                    Role.ADMIN,
                    new AttendanceCorrectionRequest(0L, "PRESENT", "Concurrent correction", null));
            return new CorrectionResult(true, null);
        } catch (ResponseStatusException exception) {
            return new CorrectionResult(false, exception);
        }
    }

    private Fixture createFixture(
            boolean createAttendance,
            String sessionStatus,
            boolean createAbsenceCase,
            boolean createPendingNotification,
            boolean createApprovedLeave) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        long departmentId = insert("insert into department(name) values (?)", "Department-" + suffix);
        long programmeId = insert(
                "insert into programme(department_id, name) values (?, ?)", departmentId, "Programme-" + suffix);
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate endDate = LocalDate.of(2027, 12, 31);
        long academicYearId = insert(
                "insert into academic_year(name, start_date, end_date) values (?, ?, ?)",
                "Year-" + suffix,
                Date.valueOf(startDate),
                Date.valueOf(endDate));
        long semesterId = insert(
                "insert into semester(academic_year_id, name, start_date, end_date) values (?, ?, ?, ?)",
                academicYearId,
                "Semester-" + suffix.substring(0, 16),
                Date.valueOf(startDate),
                Date.valueOf(endDate));
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
        long adminAccountId = insert(
                "insert into user_account(login_identifier, password_hash, role, status) values (?, ?, ?, ?)",
                "admin-" + suffix,
                "test-hash",
                "ADMIN",
                "ACTIVE");
        long professorAccountId = insert(
                "insert into user_account(login_identifier, password_hash, role, status) values (?, ?, ?, ?)",
                "professor-" + suffix,
                "test-hash",
                "PROFESSOR",
                "ACTIVE");
        long studentAccountId = insert(
                "insert into user_account(login_identifier, password_hash, role, status) values (?, ?, ?, ?)",
                "student-" + suffix,
                "test-hash",
                "STUDENT",
                "ACTIVE");
        long professorId = insert(
                "insert into professor_profile(account_id, employee_identifier, department_id, display_name) "
                        + "values (?, ?, ?, ?)",
                professorAccountId,
                "EMP-" + suffix,
                departmentId,
                "Professor-" + suffix);
        long studentId = insert(
                "insert into student_profile(account_id, college_identifier, programme_id, section_id, "
                        + "display_name, status) values (?, ?, ?, ?, ?, ?)",
                studentAccountId,
                "COL-" + suffix,
                programmeId,
                sectionId,
                "Student-" + suffix,
                "ACTIVE");
        long assignmentId = insert(
                "insert into professor_assignment(professor_id, offering_id, status) values (?, ?, ?)",
                professorId,
                offeringId,
                "ACTIVE");
        LocalDate sessionDate = LocalDate.of(2026, 11, 11);
        long sessionId = insert(
                "insert into class_session(assignment_id, session_date, start_time, end_time, status) "
                        + "values (?, ?, ?, ?, ?)",
                assignmentId,
                Date.valueOf(sessionDate),
                java.sql.Time.valueOf(LocalTime.of(9, 0)),
                java.sql.Time.valueOf(LocalTime.of(10, 0)),
                sessionStatus);
        jdbcTemplate.update(
                "insert into session_roster(session_id, student_id) values (?, ?)", sessionId, studentId);

        Long attendanceId = null;
        if (createAttendance) {
            attendanceId = insert(
                    "insert into attendance_record(session_id, student_id, submitted_by_professor_id, status, "
                            + "version, submitted_at, source_action) values (?, ?, ?, ?, ?, ?, ?)",
                    sessionId,
                    studentId,
                    professorId,
                    "ABSENT",
                    0L,
                    Timestamp.from(Instant.parse("2026-11-11T08:00:00Z")),
                    "PROFESSOR_SUBMIT");
            if (createAbsenceCase) {
                jdbcTemplate.update(
                        "insert into absence_case(attendance_id, status, created_at, updated_at) values (?, ?, ?, ?)",
                        attendanceId,
                        "UNEXPLAINED",
                        Timestamp.from(Instant.parse("2026-11-11T08:00:00Z")),
                        Timestamp.from(Instant.parse("2026-11-11T08:00:00Z")));
            }
            if (createPendingNotification) {
                jdbcTemplate.update(
                        "insert into notification_outbox(student_id, parent_id, source_type, source_id, "
                                + "template_name, payload_json, idempotency_key, status, attempt_count, created_at, "
                                + "updated_at) values (?, null, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        studentId,
                        "ATTENDANCE",
                        attendanceId,
                        "TPL-ATT-ABSENT",
                        "{\"status\":\"ABSENT\"}",
                        "initial-absence-" + suffix,
                        "PENDING",
                        0,
                        Timestamp.from(Instant.parse("2026-11-11T08:01:00Z")),
                        Timestamp.from(Instant.parse("2026-11-11T08:01:00Z")));
            }
        }
        if (createApprovedLeave) {
            jdbcTemplate.update(
                    "insert into leave_request(student_id, requester_account_id, start_date, end_date, "
                            + "reason_category, reason, status, submitted_at, reviewed_at) "
                            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    studentId,
                    adminAccountId,
                    Date.valueOf(sessionDate),
                    Date.valueOf(sessionDate),
                    "MEDICAL",
                    "Approved test leave",
                    "APPROVED",
                    Timestamp.from(Instant.parse("2026-11-10T08:00:00Z")),
                    Timestamp.from(Instant.parse("2026-11-10T09:00:00Z")));
        }
        return new Fixture(sessionId, attendanceId, studentId, professorAccountId, adminAccountId);
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

    private record Fixture(
            long sessionId,
            Long attendanceId,
            long studentId,
            long professorAccountId,
            long adminAccountId) {}

    private record CorrectionResult(boolean succeeded, ResponseStatusException conflict) {}
}

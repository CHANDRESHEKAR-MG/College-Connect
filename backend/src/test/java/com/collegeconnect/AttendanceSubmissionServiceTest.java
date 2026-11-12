package com.collegeconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.collegeconnect.dto.AttendanceMarkRequest;
import com.collegeconnect.dto.AttendanceSubmissionRequest;
import com.collegeconnect.entity.AuditLog;
import com.collegeconnect.entity.AttendanceRecord;
import com.collegeconnect.entity.ClassSession;
import com.collegeconnect.entity.LeaveRequest;
import com.collegeconnect.entity.NotificationOutbox;
import com.collegeconnect.entity.ParentProfile;
import com.collegeconnect.entity.ParentStudentLink;
import com.collegeconnect.entity.ProfessorAssignment;
import com.collegeconnect.entity.ProfessorProfile;
import com.collegeconnect.entity.SessionRoster;
import com.collegeconnect.entity.StudentProfile;
import com.collegeconnect.entity.UserAccount;
import com.collegeconnect.repository.AbsenceCaseRepository;
import com.collegeconnect.repository.AuditLogRepository;
import com.collegeconnect.repository.AttendanceRecordRepository;
import com.collegeconnect.repository.ClassSessionRepository;
import com.collegeconnect.repository.LeaveRequestRepository;
import com.collegeconnect.repository.NotificationOutboxRepository;
import com.collegeconnect.repository.NotificationPreferenceRepository;
import com.collegeconnect.repository.ParentStudentLinkRepository;
import com.collegeconnect.repository.ProfessorProfileRepository;
import com.collegeconnect.repository.SessionRosterRepository;
import com.collegeconnect.repository.UserAccountRepository;
import com.collegeconnect.security.Role;
import com.collegeconnect.service.AttendanceSubmissionService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class AttendanceSubmissionServiceTest {

    private ClassSessionRepository classSessionRepository;
    private SessionRosterRepository sessionRosterRepository;
    private ProfessorProfileRepository professorProfileRepository;
    private AttendanceRecordRepository attendanceRecordRepository;
    private AbsenceCaseRepository absenceCaseRepository;
    private LeaveRequestRepository leaveRequestRepository;
    private NotificationOutboxRepository notificationOutboxRepository;
    private NotificationPreferenceRepository notificationPreferenceRepository;
    private ParentStudentLinkRepository parentStudentLinkRepository;
    private UserAccountRepository userAccountRepository;
    private AuditLogRepository auditLogRepository;
    private AttendanceSubmissionService attendanceSubmissionService;

    @BeforeEach
    void setUp() {
        classSessionRepository = mock(ClassSessionRepository.class);
        sessionRosterRepository = mock(SessionRosterRepository.class);
        professorProfileRepository = mock(ProfessorProfileRepository.class);
        attendanceRecordRepository = mock(AttendanceRecordRepository.class);
        absenceCaseRepository = mock(AbsenceCaseRepository.class);
        leaveRequestRepository = mock(LeaveRequestRepository.class);
        notificationOutboxRepository = mock(NotificationOutboxRepository.class);
        notificationPreferenceRepository = mock(NotificationPreferenceRepository.class);
        parentStudentLinkRepository = mock(ParentStudentLinkRepository.class);
        userAccountRepository = mock(UserAccountRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);
        attendanceSubmissionService = new AttendanceSubmissionService(
                classSessionRepository,
                sessionRosterRepository,
                leaveRequestRepository,
                professorProfileRepository,
                attendanceRecordRepository,
                absenceCaseRepository,
                parentStudentLinkRepository,
                notificationPreferenceRepository,
                notificationOutboxRepository,
                userAccountRepository,
                auditLogRepository);
    }

    @Test
    void professorCannotSubmitAttendanceForAnotherProfessorsSession() {
        ClassSession session = sessionAssignedTo(17L);
        when(classSessionRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> attendanceSubmissionService.submit(
                        42L,
                        18L,
                        Role.PROFESSOR,
                        new AttendanceSubmissionRequest(List.of(new AttendanceMarkRequest(31L, "PRESENT")))))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(sessionRosterRepository, never())
                .findBySession_IdOrderByStudent_CollegeIdentifierAsc(42L);
    }

    @Test
    void submissionMustContainTheCompleteRoster() {
        ClassSession session = sessionAssignedTo(17L);
        List<SessionRoster> roster = List.of(rosterEntry(31L), rosterEntry(32L));
        when(classSessionRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(session));
        when(sessionRosterRepository.findBySession_IdOrderByStudent_CollegeIdentifierAsc(42L))
                .thenReturn(roster);

        assertThatThrownBy(() -> attendanceSubmissionService.submit(
                        42L,
                        17L,
                        Role.PROFESSOR,
                        new AttendanceSubmissionRequest(List.of(new AttendanceMarkRequest(31L, "PRESENT")))))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(attendanceRecordRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void duplicateStudentMarksAreRejected() {
        ClassSession session = sessionAssignedTo(17L);
        List<SessionRoster> roster = List.of(rosterEntry(31L));
        when(classSessionRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(session));
        when(sessionRosterRepository.findBySession_IdOrderByStudent_CollegeIdentifierAsc(42L))
                .thenReturn(roster);

        assertThatThrownBy(() -> attendanceSubmissionService.submit(
                        42L,
                        17L,
                        Role.PROFESSOR,
                        new AttendanceSubmissionRequest(List.of(
                                new AttendanceMarkRequest(31L, "PRESENT"),
                                new AttendanceMarkRequest(31L, "ABSENT")))))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(attendanceRecordRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void adminSubmissionIsAudited() {
        ClassSession session = sessionAssignedTo(17L);
        List<SessionRoster> roster = List.of(rosterEntry(31L));
        when(classSessionRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(session));
        when(sessionRosterRepository.findBySession_IdOrderByStudent_CollegeIdentifierAsc(42L))
                .thenReturn(roster);
        when(attendanceRecordRepository.saveAllAndFlush(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        UserAccount admin = mock(UserAccount.class);
        when(userAccountRepository.findById(99L)).thenReturn(Optional.of(admin));

        attendanceSubmissionService.submit(
                42L,
                99L,
                Role.ADMIN,
                new AttendanceSubmissionRequest(List.of(new AttendanceMarkRequest(31L, "PRESENT"))));

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("ATTENDANCE_ADMIN_OVERRIDE");
    }

    @Test
    void approvedLeaveStudentCannotBeSubmittedAsOrdinarilyAbsent() {
        ClassSession session = sessionAssignedTo(17L);
        List<SessionRoster> roster = List.of(rosterEntry(31L));
        when(classSessionRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(session));
        when(sessionRosterRepository.findBySession_IdOrderByStudent_CollegeIdentifierAsc(42L))
                .thenReturn(roster);
        LeaveRequest approvedLeave = approvedLeaveFor(31L);
        when(leaveRequestRepository.findApprovedForStudentsOnDate(List.of(31L), session.getSessionDate()))
                .thenReturn(List.of(approvedLeave));

        assertThatThrownBy(() -> attendanceSubmissionService.submit(
                        42L,
                        17L,
                        Role.PROFESSOR,
                        new AttendanceSubmissionRequest(List.of(new AttendanceMarkRequest(31L, "ABSENT")))))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception).hasMessageContaining("AUTHORIZED_LEAVE");
                });

        verify(attendanceRecordRepository, never()).saveAllAndFlush(anyList());
        verify(absenceCaseRepository, never()).saveAll(anyList());
        verify(notificationOutboxRepository, never()).saveAll(anyList());
    }

    @Test
    void approvedLeaveIsRecordedAsAuthorizedLeaveWithoutAnAbsenceCase() {
        ClassSession session = sessionAssignedTo(17L);
        List<SessionRoster> roster = List.of(rosterEntry(31L));
        AtomicReference<List<AttendanceRecord>> savedRecords = new AtomicReference<>();
        when(classSessionRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(session));
        when(sessionRosterRepository.findBySession_IdOrderByStudent_CollegeIdentifierAsc(42L))
                .thenReturn(roster);
        LeaveRequest approvedLeave = approvedLeaveFor(31L);
        when(leaveRequestRepository.findApprovedForStudentsOnDate(List.of(31L), session.getSessionDate()))
                .thenReturn(List.of(approvedLeave));
        ProfessorProfile submittingProfessor = mock(ProfessorProfile.class);
        when(professorProfileRepository.findByAccount_Id(17L)).thenReturn(Optional.of(submittingProfessor));
        when(attendanceRecordRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> {
            List<AttendanceRecord> records = invocation.getArgument(0);
            savedRecords.set(records);
            return records;
        });

        attendanceSubmissionService.submit(
                42L,
                17L,
                Role.PROFESSOR,
                new AttendanceSubmissionRequest(List.of(new AttendanceMarkRequest(31L, "AUTHORIZED_LEAVE"))));

        assertThat(savedRecords.get()).singleElement().extracting(AttendanceRecord::getStatus)
                .isEqualTo("AUTHORIZED_LEAVE");
        verify(absenceCaseRepository).saveAll(argThat(cases -> !cases.iterator().hasNext()));
        verify(notificationOutboxRepository).saveAll(argThat(events -> !events.iterator().hasNext()));
    }

    @Test
    void mixedRosterCreatesAbsenceOnlyForStudentWithoutApprovedLeave() {
        ClassSession session = sessionAssignedTo(17L);
        List<SessionRoster> roster = List.of(rosterEntry(31L), rosterEntry(32L));
        AtomicReference<List<AttendanceRecord>> savedRecords = new AtomicReference<>();
        when(classSessionRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(session));
        when(sessionRosterRepository.findBySession_IdOrderByStudent_CollegeIdentifierAsc(42L))
                .thenReturn(roster);
        LeaveRequest approvedLeave = approvedLeaveFor(31L);
        when(leaveRequestRepository.findApprovedForStudentsOnDate(List.of(31L, 32L), session.getSessionDate()))
                .thenReturn(List.of(approvedLeave));
        ProfessorProfile submittingProfessor = mock(ProfessorProfile.class);
        when(professorProfileRepository.findByAccount_Id(17L)).thenReturn(Optional.of(submittingProfessor));
        when(attendanceRecordRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> {
            List<AttendanceRecord> records = invocation.getArgument(0);
            savedRecords.set(records);
            return records;
        });

        attendanceSubmissionService.submit(
                42L,
                17L,
                Role.PROFESSOR,
                new AttendanceSubmissionRequest(List.of(
                        new AttendanceMarkRequest(31L, "AUTHORIZED_LEAVE"),
                        new AttendanceMarkRequest(32L, "ABSENT"))));

        assertThat(savedRecords.get()).extracting(AttendanceRecord::getStatus)
                .containsExactly("AUTHORIZED_LEAVE", "ABSENT");
        verify(absenceCaseRepository).saveAll(argThat(cases -> {
            var iterator = cases.iterator();
            return iterator.hasNext()
                    && iterator.next().getAttendance().getStudent().getId().equals(32L)
                    && !iterator.hasNext();
        }));
        verify(notificationOutboxRepository).saveAll(argThat(events -> {
            var iterator = events.iterator();
            return iterator.hasNext()
                    && iterator.next().getSourceType().equals("ATTENDANCE")
                    && !iterator.hasNext();
        }));
    }

    @Test
    void absentStudentWithoutApprovedLeaveCreatesAbsenceCase() {
        ClassSession session = sessionAssignedTo(17L);
        SessionRoster rosterEntry = rosterEntry(31L);
        StudentProfile student = rosterEntry.getStudent();
        List<SessionRoster> roster = List.of(rosterEntry);
        ParentProfile parent = mock(ParentProfile.class);
        when(parent.getId()).thenReturn(22L);
        ParentStudentLink activeLink = activeOptedInParentLink(parent);
        when(parentStudentLinkRepository.findByStudent_Id(31L))
                .thenReturn(List.of(activeLink));
        when(notificationPreferenceRepository.findByParent_IdAndCategory(22L, "ATTENDANCE_ABSENT"))
                .thenReturn(Optional.empty());
        when(classSessionRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(session));
        when(sessionRosterRepository.findBySession_IdOrderByStudent_CollegeIdentifierAsc(42L))
                .thenReturn(roster);
        ProfessorProfile submittingProfessor = mock(ProfessorProfile.class);
        when(professorProfileRepository.findByAccount_Id(17L)).thenReturn(Optional.of(submittingProfessor));
        when(attendanceRecordRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> {
            List<AttendanceRecord> savedRecords = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedRecords.get(0), "id", 410L);
            return savedRecords;
        });

        attendanceSubmissionService.submit(
                42L,
                17L,
                Role.PROFESSOR,
                new AttendanceSubmissionRequest(List.of(new AttendanceMarkRequest(31L, "ABSENT"))));

        verify(absenceCaseRepository).saveAll(argThat(cases -> {
            var iterator = cases.iterator();
            return iterator.hasNext() && iterator.next().getAttendance().getStudent().getId().equals(31L)
                    && !iterator.hasNext();
        }));
        ArgumentCaptor<Iterable<NotificationOutbox>> outboxCaptor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(notificationOutboxRepository).saveAll(outboxCaptor.capture());
        List<NotificationOutbox> events = new java.util.ArrayList<>();
        outboxCaptor.getValue().forEach(events::add);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getSourceType()).isEqualTo("ATTENDANCE");
            assertThat(event.getParent()).isSameAs(parent);
            assertThat(event.getStudent()).isSameAs(student);
            assertThat(event.getSourceId()).isEqualTo(410L);
            assertThat(event.getTemplateName()).isEqualTo("TPL-ATT-ABSENT");
            assertThat(event.getIdempotencyKey()).isEqualTo("attendance:42:31:22:ABSENT");
            assertThat(event.getStatus()).isEqualTo("PENDING");
            assertThat(event.getPayloadJson())
                    .isEqualTo("{\"sessionId\":42,\"studentId\":31,\"attendanceId\":410,\"status\":\"ABSENT\"}");
        });
    }

    @Test
    void authorizedLeaveIsRejectedWithoutApprovedLeave() {
        ClassSession session = sessionAssignedTo(17L);
        SessionRoster rosterEntry = rosterEntry(31L);
        List<SessionRoster> roster = List.of(rosterEntry);
        when(classSessionRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(session));
        when(sessionRosterRepository.findBySession_IdOrderByStudent_CollegeIdentifierAsc(42L))
                .thenReturn(roster);

        assertThatThrownBy(() -> attendanceSubmissionService.submit(
                        42L,
                        17L,
                        Role.PROFESSOR,
                        new AttendanceSubmissionRequest(
                                List.of(new AttendanceMarkRequest(31L, "AUTHORIZED_LEAVE")))))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(attendanceRecordRepository, never()).saveAllAndFlush(anyList());
    }

    private ClassSession sessionAssignedTo(Long accountId) {
        UserAccount account = mock(UserAccount.class);
        when(account.getId()).thenReturn(accountId);
        ProfessorProfile professor = mock(ProfessorProfile.class);
        when(professor.getAccount()).thenReturn(account);
        ProfessorAssignment assignment = mock(ProfessorAssignment.class);
        when(assignment.getProfessor()).thenReturn(professor);
        ClassSession session = mock(ClassSession.class);
        when(session.getId()).thenReturn(42L);
        when(session.getAssignment()).thenReturn(assignment);
        when(session.getStatus()).thenReturn("SCHEDULED");
        when(session.getSessionDate()).thenReturn(LocalDate.of(2026, 11, 11));
        return session;
    }

    private SessionRoster rosterEntry(Long studentId) {
        StudentProfile student = mock(StudentProfile.class);
        when(student.getId()).thenReturn(studentId);
        SessionRoster entry = mock(SessionRoster.class);
        when(entry.getStudent()).thenReturn(student);
        return entry;
    }

    private ParentStudentLink activeOptedInParentLink(ParentProfile parent) {
        org.mockito.Mockito.doReturn(true).when(parent).hasVerifiedMobile();
        org.mockito.Mockito.doReturn(true).when(parent).isWhatsappOptIn();
        ParentStudentLink link = mock(ParentStudentLink.class);
        org.mockito.Mockito.doReturn(true).when(link).isActiveVerified();
        when(link.getParent()).thenReturn(parent);
        return link;
    }

    private LeaveRequest approvedLeaveFor(Long studentId) {
        StudentProfile student = mock(StudentProfile.class);
        when(student.getId()).thenReturn(studentId);
        LeaveRequest leave = mock(LeaveRequest.class);
        when(leave.getStudent()).thenReturn(student);
        return leave;
    }

}

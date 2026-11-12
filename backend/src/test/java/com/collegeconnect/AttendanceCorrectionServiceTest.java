package com.collegeconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.collegeconnect.dto.AttendanceCorrectionHistoryResponse;
import com.collegeconnect.dto.AttendanceCorrectionRequest;
import com.collegeconnect.dto.AttendanceCorrectionResponse;
import com.collegeconnect.entity.AbsenceCase;
import com.collegeconnect.entity.AttendanceCorrection;
import com.collegeconnect.entity.AttendanceRecord;
import com.collegeconnect.entity.ClassSession;
import com.collegeconnect.entity.LeaveRequest;
import com.collegeconnect.entity.NotificationOutbox;
import com.collegeconnect.entity.NotificationPreference;
import com.collegeconnect.entity.ParentProfile;
import com.collegeconnect.entity.ParentStudentLink;
import com.collegeconnect.entity.ProfessorProfile;
import com.collegeconnect.entity.StudentProfile;
import com.collegeconnect.entity.UserAccount;
import com.collegeconnect.repository.AbsenceCaseRepository;
import com.collegeconnect.repository.AttendanceCorrectionRepository;
import com.collegeconnect.repository.AttendanceRecordRepository;
import com.collegeconnect.repository.LeaveRequestRepository;
import com.collegeconnect.repository.NotificationOutboxRepository;
import com.collegeconnect.repository.NotificationPreferenceRepository;
import com.collegeconnect.repository.ParentStudentLinkRepository;
import com.collegeconnect.repository.UserAccountRepository;
import com.collegeconnect.security.Role;
import com.collegeconnect.service.AttendanceCorrectionService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AttendanceCorrectionServiceTest {

    private AttendanceRecordRepository attendanceRecordRepository;
    private AttendanceCorrectionRepository attendanceCorrectionRepository;
    private AbsenceCaseRepository absenceCaseRepository;
    private NotificationOutboxRepository notificationOutboxRepository;
    private ParentStudentLinkRepository parentStudentLinkRepository;
    private NotificationPreferenceRepository notificationPreferenceRepository;
    private LeaveRequestRepository leaveRequestRepository;
    private UserAccountRepository userAccountRepository;
    private AttendanceCorrectionService service;

    @BeforeEach
    void setUp() {
        attendanceRecordRepository = mock(AttendanceRecordRepository.class);
        attendanceCorrectionRepository = mock(AttendanceCorrectionRepository.class);
        absenceCaseRepository = mock(AbsenceCaseRepository.class);
        notificationOutboxRepository = mock(NotificationOutboxRepository.class);
        parentStudentLinkRepository = mock(ParentStudentLinkRepository.class);
        notificationPreferenceRepository = mock(NotificationPreferenceRepository.class);
        leaveRequestRepository = mock(LeaveRequestRepository.class);
        userAccountRepository = mock(UserAccountRepository.class);
        service = new AttendanceCorrectionService(
                attendanceRecordRepository,
                leaveRequestRepository,
                attendanceCorrectionRepository,
                absenceCaseRepository,
                parentStudentLinkRepository,
                notificationOutboxRepository,
                notificationPreferenceRepository,
                userAccountRepository,
                false,
                java.time.Duration.ZERO);
    }

    @Test
    void professorCannotCorrectUntilCorrectionPolicyIsConfigured() {
        assertThatThrownBy(() -> service.correct(
                        10L,
                        15L,
                        Role.PROFESSOR,
                        new AttendanceCorrectionRequest(0L, "PRESENT", "Roster typo", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(attendanceRecordRepository, never()).findByIdForUpdate(10L);
    }

    @Test
    void staleExpectedVersionIsRejected() {
        AttendanceRecord attendance = mock(AttendanceRecord.class);
        ClassSession session = mock(ClassSession.class);
        when(session.getStatus()).thenReturn("SCHEDULED");
        when(attendance.getSession()).thenReturn(session);
        when(attendance.getVersion()).thenReturn(2L);
        when(attendanceRecordRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(attendance));

        assertThatThrownBy(() -> service.correct(
                        10L,
                        15L,
                        Role.ADMIN,
                        new AttendanceCorrectionRequest(1L, "PRESENT", "Correction reason", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception).hasMessageContaining("Attendance was changed");
                });
        verify(session, never()).getStatus();
    }

    @Test
    void correctionFromAbsentToPresentCancelsOnlyUnacceptedNotifications() {
        AttendanceRecord attendance = attendanceRecord("ABSENT");
        when(attendanceRecordRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(attendance));
        UserAccount correctionActor = actor();
        when(userAccountRepository.findById(15L)).thenReturn(Optional.of(correctionActor));
        when(attendanceCorrectionRepository.findHighestSequence(10L)).thenReturn(0);
        when(attendanceCorrectionRepository.saveAndFlush(any(AttendanceCorrection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AbsenceCase existingAbsence = new AbsenceCase(attendance, "UNEXPLAINED", Instant.now());
        when(absenceCaseRepository.findByAttendance_Id(10L)).thenReturn(Optional.of(existingAbsence));
        NotificationOutbox pending = absenceNotification("PENDING");
        NotificationOutbox retryable = absenceNotification("FAILED_RETRYABLE");
        NotificationOutbox accepted = absenceNotification("SENT");
        when(notificationOutboxRepository.findBySourceTypeAndSourceIdForUpdate("ATTENDANCE", 10L))
                .thenReturn(List.of(pending, retryable, accepted));
        ParentStudentLink parentLink = activeOptedInParentLink();
        when(parentStudentLinkRepository.findByStudent_Id(31L)).thenReturn(List.of(parentLink));
        when(notificationPreferenceRepository.findByParent_IdAndCategory(22L, "ATTENDANCE_PRESENT"))
                .thenReturn(Optional.empty());

        service.correct(10L, 15L, Role.ADMIN, new AttendanceCorrectionRequest(0L, "PRESENT", "Mistaken mark", null));

        assertThat(attendance.getStatus()).isEqualTo("PRESENT");
        assertThat(pending.getStatus()).isEqualTo("CANCELLED");
        assertThat(retryable.getStatus()).isEqualTo("CANCELLED");
        assertThat(accepted.getStatus()).isEqualTo("SENT");
        assertThat(existingAbsence.getStatus()).isEqualTo("CLOSED_CORRECTED");
        ArgumentCaptor<NotificationOutbox> eventCaptor = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(notificationOutboxRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getSourceType()).isEqualTo("ATTENDANCE_CORRECTION");
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo("SKIPPED_PREFERENCE");
    }

    @Test
    void correctionFromPresentToAbsentOpensAbsenceCase() {
        AttendanceRecord attendance = attendanceRecord("PRESENT");
        when(attendanceRecordRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(attendance));
        UserAccount correctionActor = actor();
        when(userAccountRepository.findById(15L)).thenReturn(Optional.of(correctionActor));
        when(attendanceCorrectionRepository.findHighestSequence(10L)).thenReturn(0);
        when(attendanceCorrectionRepository.saveAndFlush(any(AttendanceCorrection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(absenceCaseRepository.findByAttendance_Id(10L)).thenReturn(Optional.empty());
        ParentStudentLink parentLink = activeOptedInParentLink();
        when(parentStudentLinkRepository.findByStudent_Id(31L)).thenReturn(List.of(parentLink));
        when(notificationPreferenceRepository.findByParent_IdAndCategory(22L, "ATTENDANCE_ABSENT"))
                .thenReturn(Optional.empty());

        service.correct(10L, 15L, Role.ADMIN, new AttendanceCorrectionRequest(0L, "ABSENT", "Missed mark", "Checked"));

        assertThat(attendance.getStatus()).isEqualTo("ABSENT");
        ArgumentCaptor<AbsenceCase> caseCaptor = ArgumentCaptor.forClass(AbsenceCase.class);
        verify(absenceCaseRepository).save(caseCaptor.capture());
        assertThat(caseCaptor.getValue().getStatus()).isEqualTo("UNEXPLAINED");
        ArgumentCaptor<NotificationOutbox> eventCaptor = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(notificationOutboxRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getSourceType()).isEqualTo("ATTENDANCE_CORRECTION");
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void correctionFromAbsentToAuthorizedLeaveRequiresApprovedLeaveAndClosesAbsenceCase() {
        AttendanceRecord attendance = attendanceRecord("ABSENT");
        when(attendanceRecordRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(attendance));
        UserAccount correctionActor = actor();
        when(userAccountRepository.findById(15L)).thenReturn(Optional.of(correctionActor));
        when(attendanceCorrectionRepository.findHighestSequence(10L)).thenReturn(0);
        when(attendanceCorrectionRepository.saveAndFlush(any(AttendanceCorrection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AbsenceCase existingAbsence = new AbsenceCase(attendance, "UNEXPLAINED", Instant.now());
        when(absenceCaseRepository.findByAttendance_Id(10L)).thenReturn(Optional.of(existingAbsence));
        LeaveRequest approvedLeave = mock(LeaveRequest.class);
        when(leaveRequestRepository.findApprovedForStudentsOnDate(
                        List.of(31L), java.time.LocalDate.of(2026, 11, 11)))
                .thenReturn(List.of(approvedLeave));
        ParentStudentLink parentLink = activeOptedInParentLink();
        NotificationPreference absentPreference = mock(NotificationPreference.class);
        when(absentPreference.isEnabled()).thenReturn(false);
        when(parentStudentLinkRepository.findByStudent_Id(31L)).thenReturn(List.of(parentLink));
        when(notificationPreferenceRepository.findByParent_IdAndCategory(22L, "ATTENDANCE_ABSENT"))
                .thenReturn(Optional.of(absentPreference));

        AttendanceCorrectionResponse response = service.correct(
                10L,
                15L,
                Role.ADMIN,
                new AttendanceCorrectionRequest(0L, "AUTHORIZED_LEAVE", "Approved leave", "Verified"));

        assertThat(attendance.getStatus()).isEqualTo("AUTHORIZED_LEAVE");
        assertThat(existingAbsence.getStatus()).isEqualTo("CLOSED_CORRECTED");
        assertThat(response.previousStatus()).isEqualTo("ABSENT");
        assertThat(response.newStatus()).isEqualTo("AUTHORIZED_LEAVE");
        assertThat(response.reason()).isEqualTo("Approved leave");
        ArgumentCaptor<NotificationOutbox> notificationCaptor = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(notificationOutboxRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getStatus()).isEqualTo("SKIPPED_PREFERENCE");
        verify(leaveRequestRepository).findApprovedForStudentsOnDate(
                List.of(31L), java.time.LocalDate.of(2026, 11, 11));
    }

    @Test
    void correctionToAuthorizedLeaveIsRejectedWithoutApprovedLeave() {
        AttendanceRecord attendance = attendanceRecord("ABSENT");
        when(attendanceRecordRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(attendance));
        when(leaveRequestRepository.findApprovedForStudentsOnDate(
                        List.of(31L), java.time.LocalDate.of(2026, 11, 11)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.correct(
                        10L,
                        15L,
                        Role.ADMIN,
                        new AttendanceCorrectionRequest(0L, "AUTHORIZED_LEAVE", "Approved leave", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(attendanceCorrectionRepository, never()).saveAndFlush(any(AttendanceCorrection.class));
    }

    @Test
    void correctionHistoryExposesAuditDetailsAndPreservesOriginalSubmission() {
        AttendanceRecord attendance = attendanceRecord("ABSENT");
        Instant originalSubmittedAt = attendance.getSubmittedAt();
        ProfessorProfile originalSubmitter = attendance.getSubmittedBy();
        when(attendanceRecordRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(attendance));
        when(attendanceRecordRepository.findById(10L)).thenReturn(Optional.of(attendance));
        UserAccount actor = actor();
        when(userAccountRepository.findById(15L)).thenReturn(Optional.of(actor));
        when(attendanceCorrectionRepository.findHighestSequence(10L)).thenReturn(0);
        when(attendanceCorrectionRepository.saveAndFlush(any(AttendanceCorrection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(absenceCaseRepository.findByAttendance_Id(10L)).thenReturn(Optional.empty());
        when(parentStudentLinkRepository.findByStudent_Id(31L)).thenReturn(List.of());

        AttendanceCorrectionResponse result = service.correct(
                10L, 15L, Role.ADMIN, new AttendanceCorrectionRequest(0L, "PRESENT", "Duplicate entry", "Verified"));

        ArgumentCaptor<AttendanceCorrection> correctionCaptor = ArgumentCaptor.forClass(AttendanceCorrection.class);
        verify(attendanceCorrectionRepository).saveAndFlush(correctionCaptor.capture());
        AttendanceCorrection savedCorrection = correctionCaptor.getValue();
        when(attendanceCorrectionRepository.findByAttendance_IdOrderByCorrectionSequenceAsc(10L))
                .thenReturn(List.of(savedCorrection));
        AttendanceCorrectionHistoryResponse history = service.getHistory(10L, 15L, Role.ADMIN);
        AttendanceCorrectionResponse historyEntry = history.corrections().get(0);

        assertThat(result.previousStatus()).isEqualTo("ABSENT");
        assertThat(result.newStatus()).isEqualTo("PRESENT");
        assertThat(result.actorAccountId()).isEqualTo(15L);
        assertThat(result.actorLoginIdentifier()).isEqualTo("admin@example.test");
        assertThat(result.actorRole()).isEqualTo("ADMIN");
        assertThat(result.reason()).isEqualTo("Duplicate entry");
        assertThat(result.comment()).isEqualTo("Verified");
        assertThat(result.correctionSequence()).isEqualTo(1);
        assertThat(result.correctedAt()).isNotNull();
        assertThat(historyEntry).isEqualTo(result);
        assertThat(history.currentStatus()).isEqualTo("PRESENT");
        assertThat(history.originalSubmitter()).isEqualTo("Original Professor");
        assertThat(history.submittedAt()).isEqualTo(originalSubmittedAt);
        assertThat(attendance.getSubmittedBy()).isSameAs(originalSubmitter);
        assertThat(attendance.getSubmittedAt()).isEqualTo(originalSubmittedAt);
    }

    @Test
    void concurrentCorrectionsWithSameExpectedVersionAllowOnlyOneToSucceed() throws Exception {
        AttendanceRecord attendance = attendanceRecord("ABSENT");
        AtomicInteger versionReads = new AtomicInteger();
        when(attendance.getVersion()).thenAnswer(invocation -> (long) versionReads.getAndIncrement());
        when(attendanceRecordRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(attendance));
        UserAccount correctionActor = actor();
        when(userAccountRepository.findById(15L)).thenReturn(Optional.of(correctionActor));
        when(attendanceCorrectionRepository.findHighestSequence(10L)).thenReturn(0);
        when(attendanceCorrectionRepository.saveAndFlush(any(AttendanceCorrection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(absenceCaseRepository.findByAttendance_Id(10L)).thenReturn(Optional.of(
                new AbsenceCase(attendance, "UNEXPLAINED", Instant.now())));
        when(notificationOutboxRepository.findBySourceTypeAndSourceIdForUpdate("ATTENDANCE", 10L))
                .thenReturn(List.of());
        when(parentStudentLinkRepository.findByStudent_Id(31L)).thenReturn(List.of());

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> tryCorrection(start));
            Future<Boolean> second = executor.submit(() -> tryCorrection(start));
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean tryCorrection(CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            service.correct(
                    10L, 15L, Role.ADMIN, new AttendanceCorrectionRequest(0L, "PRESENT", "Concurrent correction", null));
            return true;
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.CONFLICT) {
                return false;
            }
            throw exception;
        }
    }

    private AttendanceRecord attendanceRecord(String status) {
        AttendanceRecord attendance = mock(AttendanceRecord.class);
        AtomicReference<String> currentStatus = new AtomicReference<>(status);
        ClassSession session = mock(ClassSession.class);
        when(session.getId()).thenReturn(42L);
        when(session.getStatus()).thenReturn("SUBMITTED");
        when(session.getSessionDate()).thenReturn(java.time.LocalDate.of(2026, 11, 11));
        when(attendance.getSession()).thenReturn(session);
        when(attendance.getStatus()).thenAnswer(invocation -> currentStatus.get());
        doAnswer(invocation -> {
                    currentStatus.set(invocation.getArgument(0));
                    return null;
                })
                .when(attendance)
                .correctStatus(anyString());
        when(attendance.getId()).thenReturn(10L);
        when(attendance.getVersion()).thenReturn(0L);
        StudentProfile student = mock(StudentProfile.class);
        when(student.getId()).thenReturn(31L);
        when(attendance.getStudent()).thenReturn(student);
        ProfessorProfile submitter = mock(ProfessorProfile.class);
        when(submitter.getDisplayName()).thenReturn("Original Professor");
        when(attendance.getSubmittedBy()).thenReturn(submitter);
        when(attendance.getSubmittedAt()).thenReturn(Instant.parse("2026-01-02T03:04:05Z"));
        return attendance;
    }

    private UserAccount actor() {
        UserAccount actor = mock(UserAccount.class);
        when(actor.getId()).thenReturn(15L);
        when(actor.getLoginIdentifier()).thenReturn("admin@example.test");
        return actor;
    }

    private ParentStudentLink activeOptedInParentLink() {
        ParentProfile parent = mock(ParentProfile.class);
        when(parent.getId()).thenReturn(22L);
        doReturn(true).when(parent).hasVerifiedMobile();
        doReturn(true).when(parent).isWhatsappOptIn();
        ParentStudentLink link = mock(ParentStudentLink.class);
        doReturn(true).when(link).isActiveVerified();
        when(link.getParent()).thenReturn(parent);
        return link;
    }

    private NotificationOutbox absenceNotification(String status) {
        return new NotificationOutbox(
                mock(StudentProfile.class),
                null,
                "ATTENDANCE",
                10L,
                "TPL-ABS",
                "{}",
                "test:" + status,
                status,
                Instant.now());
    }
}

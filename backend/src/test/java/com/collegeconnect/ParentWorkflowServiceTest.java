package com.collegeconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.collegeconnect.dto.AbsenceExplanationRequest;
import com.collegeconnect.entity.AbsenceCase;
import com.collegeconnect.entity.AttendanceRecord;
import com.collegeconnect.entity.ClassSession;
import com.collegeconnect.entity.ParentProfile;
import com.collegeconnect.entity.ParentStudentLink;
import com.collegeconnect.entity.StudentProfile;
import com.collegeconnect.repository.AbsenceCaseRepository;
import com.collegeconnect.repository.ClassSessionRepository;
import com.collegeconnect.repository.LeaveRequestRepository;
import com.collegeconnect.repository.NotificationOutboxRepository;
import com.collegeconnect.repository.NotificationPreferenceRepository;
import com.collegeconnect.repository.ParentProfileRepository;
import com.collegeconnect.repository.ParentStudentLinkRepository;
import com.collegeconnect.repository.StudentProfileRepository;
import com.collegeconnect.repository.UserAccountRepository;
import com.collegeconnect.service.ParentWorkflowService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ParentWorkflowServiceTest {

    private AbsenceCaseRepository absenceCaseRepository;
    private ClassSessionRepository classSessionRepository;
    private LeaveRequestRepository leaveRequestRepository;
    private ParentProfileRepository parentProfileRepository;
    private ParentStudentLinkRepository parentStudentLinkRepository;
    private StudentProfileRepository studentProfileRepository;
    private UserAccountRepository userAccountRepository;
    private NotificationOutboxRepository notificationOutboxRepository;
    private NotificationPreferenceRepository notificationPreferenceRepository;
    private ParentWorkflowService service;

    @BeforeEach
    void setUp() {
        absenceCaseRepository = mock(AbsenceCaseRepository.class);
        classSessionRepository = mock(ClassSessionRepository.class);
        leaveRequestRepository = mock(LeaveRequestRepository.class);
        parentProfileRepository = mock(ParentProfileRepository.class);
        parentStudentLinkRepository = mock(ParentStudentLinkRepository.class);
        studentProfileRepository = mock(StudentProfileRepository.class);
        userAccountRepository = mock(UserAccountRepository.class);
        notificationOutboxRepository = mock(NotificationOutboxRepository.class);
        notificationPreferenceRepository = mock(NotificationPreferenceRepository.class);
        service = new ParentWorkflowService(
                absenceCaseRepository,
                classSessionRepository,
                leaveRequestRepository,
                parentProfileRepository,
                parentStudentLinkRepository,
                studentProfileRepository,
                userAccountRepository,
                notificationOutboxRepository,
                notificationPreferenceRepository);
    }

    @Test
    void parentWithoutActiveVerifiedLinkCannotSubmitAbsenceExplanation() {
        ParentProfile parent = mock(ParentProfile.class);
        when(parent.getId()).thenReturn(20L);
        when(parentProfileRepository.findByAccount_Id(10L)).thenReturn(Optional.of(parent));
        ParentStudentLink inactiveLink = mock(ParentStudentLink.class);
        when(inactiveLink.isActiveVerified()).thenReturn(false);
        when(parentStudentLinkRepository.findByParent_IdAndStudent_Id(20L, 40L))
                .thenReturn(List.of(inactiveLink));
        AbsenceCase absenceCase = absenceCase(40L);
        when(absenceCaseRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(absenceCase));

        assertThatThrownBy(() -> service.explainAbsence(
                        10L, 30L, new AbsenceExplanationRequest("Medical appointment")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void explanationRequiresActiveLinkAndDoesNotChangeAttendanceStatus() {
        ParentProfile parent = mock(ParentProfile.class);
        when(parent.getId()).thenReturn(20L);
        when(parentProfileRepository.findByAccount_Id(10L)).thenReturn(Optional.of(parent));

        ParentStudentLink activeLink = mock(ParentStudentLink.class);
        when(activeLink.isActiveVerified()).thenReturn(true);
        when(parentStudentLinkRepository.findByParent_IdAndStudent_Id(20L, 40L))
                .thenReturn(List.of(activeLink));

        AttendanceRecord attendance = mock(AttendanceRecord.class);
        AbsenceCase absenceCase = absenceCase(40L, attendance);
        when(absenceCaseRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(absenceCase));

        var response = service.explainAbsence(
                10L, 30L, new AbsenceExplanationRequest("  Medical appointment  "));

        assertThat(response.status()).isEqualTo("UNDER_REVIEW");
        assertThat(absenceCase.getExplanation()).isEqualTo("Medical appointment");
        assertThat(attendance.getStatus()).isEqualTo("ABSENT");
    }

    private AbsenceCase absenceCase(Long studentId) {
        return absenceCase(studentId, mock(AttendanceRecord.class));
    }

    private AbsenceCase absenceCase(Long studentId, AttendanceRecord attendance) {
        StudentProfile student = mock(StudentProfile.class);
        when(student.getId()).thenReturn(studentId);
        ClassSession session = mock(ClassSession.class);
        when(session.getId()).thenReturn(50L);
        when(attendance.getId()).thenReturn(60L);
        when(attendance.getStudent()).thenReturn(student);
        when(attendance.getSession()).thenReturn(session);
        when(attendance.getStatus()).thenReturn("ABSENT");
        return new AbsenceCase(attendance, "UNEXPLAINED", Instant.now());
    }
}

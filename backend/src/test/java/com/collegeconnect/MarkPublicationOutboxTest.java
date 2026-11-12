package com.collegeconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.collegeconnect.entity.CieAssessment;
import com.collegeconnect.entity.CieMark;
import com.collegeconnect.entity.StudentProfile;
import com.collegeconnect.entity.SubjectOffering;
import com.collegeconnect.entity.UserAccount;
import com.collegeconnect.repository.AuditLogRepository;
import com.collegeconnect.repository.CieAssessmentRepository;
import com.collegeconnect.repository.CieMarkHistoryRepository;
import com.collegeconnect.repository.CieMarkRepository;
import com.collegeconnect.repository.EnrollmentRepository;
import com.collegeconnect.repository.NotificationOutboxRepository;
import com.collegeconnect.repository.NotificationPreferenceRepository;
import com.collegeconnect.repository.ParentProfileRepository;
import com.collegeconnect.repository.ParentStudentLinkRepository;
import com.collegeconnect.repository.ProfessorAssignmentRepository;
import com.collegeconnect.repository.SeeAssessmentRepository;
import com.collegeconnect.repository.SeeMarkHistoryRepository;
import com.collegeconnect.repository.SeeMarkRepository;
import com.collegeconnect.repository.StudentProfileRepository;
import com.collegeconnect.repository.SubjectOfferingRepository;
import com.collegeconnect.repository.UserAccountRepository;
import com.collegeconnect.service.MarkManagementService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarkPublicationOutboxTest {

    private final CieAssessmentRepository cieAssessmentRepository = mock(CieAssessmentRepository.class);
    private final CieMarkRepository cieMarkRepository = mock(CieMarkRepository.class);
    private final CieMarkHistoryRepository cieMarkHistoryRepository = mock(CieMarkHistoryRepository.class);
    private final SeeAssessmentRepository seeAssessmentRepository = mock(SeeAssessmentRepository.class);
    private final SeeMarkRepository seeMarkRepository = mock(SeeMarkRepository.class);
    private final SeeMarkHistoryRepository seeMarkHistoryRepository = mock(SeeMarkHistoryRepository.class);
    private final SubjectOfferingRepository subjectOfferingRepository = mock(SubjectOfferingRepository.class);
    private final ProfessorAssignmentRepository professorAssignmentRepository =
            mock(ProfessorAssignmentRepository.class);
    private final EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
    private final StudentProfileRepository studentProfileRepository = mock(StudentProfileRepository.class);
    private final ParentProfileRepository parentProfileRepository = mock(ParentProfileRepository.class);
    private final ParentStudentLinkRepository parentStudentLinkRepository = mock(ParentStudentLinkRepository.class);
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final NotificationOutboxRepository notificationOutboxRepository = mock(NotificationOutboxRepository.class);
    private final NotificationPreferenceRepository notificationPreferenceRepository =
            mock(NotificationPreferenceRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);

    private MarkManagementService service;

    @BeforeEach
    void setUp() {
        service = new MarkManagementService(
                cieAssessmentRepository,
                cieMarkRepository,
                cieMarkHistoryRepository,
                seeAssessmentRepository,
                seeMarkRepository,
                seeMarkHistoryRepository,
                subjectOfferingRepository,
                professorAssignmentRepository,
                enrollmentRepository,
                studentProfileRepository,
                parentProfileRepository,
                parentStudentLinkRepository,
                userAccountRepository,
                notificationOutboxRepository,
                notificationPreferenceRepository,
                auditLogRepository);
    }

    @Test
    void publicationRecordsSkippedEventWhenStudentHasNoActiveVerifiedParentLink() {
        CieAssessment assessment = mock(CieAssessment.class);
        when(assessment.getId()).thenReturn(7L);
        when(assessment.getName()).thenReturn("Assessment 1");
        SubjectOffering offering = mock(SubjectOffering.class);
        when(offering.getId()).thenReturn(19L);
        when(assessment.getOffering()).thenReturn(offering);
        when(cieAssessmentRepository.findById(7L)).thenReturn(Optional.of(assessment));

        StudentProfile student = mock(StudentProfile.class);
        when(student.getId()).thenReturn(13L);
        CieMark mark = mock(CieMark.class);
        when(mark.getStudent()).thenReturn(student);
        when(cieMarkRepository.findByAssessment_Id(7L)).thenReturn(List.of(mark));
        when(parentStudentLinkRepository.findByStudent_Id(13L)).thenReturn(List.of());
        when(userAccountRepository.findById(5L)).thenReturn(Optional.of(mock(UserAccount.class)));

        service.publishCie(7L, 5L);

        verify(notificationOutboxRepository).saveAll(argThat(events -> {
            assertThat(events).hasSize(1);
            var event = events.iterator().next();
            assertThat(event.getParent()).isNull();
            assertThat(event.getStudent()).isSameAs(student);
            assertThat(event.getStatus()).isEqualTo("SKIPPED_NO_OPTIN");
            return true;
        }));
    }
}

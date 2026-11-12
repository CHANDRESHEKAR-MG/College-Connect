package com.collegeconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.collegeconnect.dto.AttendanceRosterResponse;
import com.collegeconnect.dto.AttendanceRosterStudentResponse;
import com.collegeconnect.entity.ClassSession;
import com.collegeconnect.entity.ProfessorAssignment;
import com.collegeconnect.entity.ProfessorProfile;
import com.collegeconnect.entity.SessionRoster;
import com.collegeconnect.entity.StudentProfile;
import com.collegeconnect.entity.Subject;
import com.collegeconnect.entity.SubjectOffering;
import com.collegeconnect.entity.UserAccount;
import com.collegeconnect.repository.ClassSessionRepository;
import com.collegeconnect.repository.LeaveRequestRepository;
import com.collegeconnect.repository.SessionRosterRepository;
import com.collegeconnect.security.Role;
import com.collegeconnect.service.AttendanceRosterService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AttendanceRosterServiceTest {

    private ClassSessionRepository classSessionRepository;
    private SessionRosterRepository sessionRosterRepository;
    private AttendanceRosterService attendanceRosterService;

    @BeforeEach
    void setUp() {
        classSessionRepository = mock(ClassSessionRepository.class);
        sessionRosterRepository = mock(SessionRosterRepository.class);
        LeaveRequestRepository leaveRequestRepository = mock(LeaveRequestRepository.class);
        attendanceRosterService =
                new AttendanceRosterService(classSessionRepository, sessionRosterRepository, leaveRequestRepository);
    }

    @Test
    void assignedProfessorReceivesSessionAndRosterDetails() {
        ClassSession session = sessionWithAssignedAccount(17L);
        when(classSessionRepository.findById(42L)).thenReturn(Optional.of(session));

        SessionRoster rosterEntry = mock(SessionRoster.class);
        StudentProfile student = mock(StudentProfile.class);
        when(student.getId()).thenReturn(31L);
        when(student.getCollegeIdentifier()).thenReturn("STU-31");
        when(student.getDisplayName()).thenReturn("Student One");
        when(rosterEntry.getStudent()).thenReturn(student);
        when(sessionRosterRepository.findBySession_IdOrderByStudent_CollegeIdentifierAsc(42L))
                .thenReturn(List.of(rosterEntry));

        AttendanceRosterResponse response =
                attendanceRosterService.getRoster(42L, 17L, Role.PROFESSOR);

        assertThat(response.sessionId()).isEqualTo(42L);
        assertThat(response.subjectCode()).isEqualTo("CS101");
        assertThat(response.students())
                .containsExactly(new AttendanceRosterStudentResponse(31L, "STU-31", "Student One", false));
    }

    @Test
    void professorCannotReadAnotherProfessorsRoster() {
        ClassSession session = sessionWithAssignedAccount(17L);
        when(classSessionRepository.findById(42L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> attendanceRosterService.getRoster(42L, 18L, Role.PROFESSOR))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(sessionRosterRepository, never())
                .findBySession_IdOrderByStudent_CollegeIdentifierAsc(42L);
    }

    @Test
    void adminCanReadAnySessionRoster() {
        ClassSession session = sessionWithAssignedAccount(17L);
        when(classSessionRepository.findById(42L)).thenReturn(Optional.of(session));
        when(sessionRosterRepository.findBySession_IdOrderByStudent_CollegeIdentifierAsc(42L))
                .thenReturn(List.of());

        AttendanceRosterResponse response =
                attendanceRosterService.getRoster(42L, 99L, Role.ADMIN);

        assertThat(response.students()).isEmpty();
    }

    @Test
    void sessionSubstituteCanReadRoster() {
        ClassSession session = sessionWithAssignedAccount(17L);
        ProfessorProfile substitute = mock(ProfessorProfile.class);
        UserAccount substituteAccount = mock(UserAccount.class);
        when(substituteAccount.getId()).thenReturn(18L);
        when(substitute.getAccount()).thenReturn(substituteAccount);
        when(session.getSubstituteProfessor()).thenReturn(substitute);
        when(classSessionRepository.findById(42L)).thenReturn(Optional.of(session));
        when(sessionRosterRepository.findBySession_IdOrderByStudent_CollegeIdentifierAsc(42L))
                .thenReturn(List.of());

        assertThat(attendanceRosterService.getRoster(42L, 18L, Role.PROFESSOR).students()).isEmpty();
    }

    @Test
    void studentCannotReadSessionRoster() {
        assertThatThrownBy(() -> attendanceRosterService.getRoster(42L, 31L, Role.STUDENT))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(classSessionRepository, never()).findById(42L);
    }

    @Test
    void missingSessionReturnsNotFound() {
        when(classSessionRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attendanceRosterService.getRoster(42L, 17L, Role.PROFESSOR))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private ClassSession sessionWithAssignedAccount(Long accountId) {
        UserAccount account = mock(UserAccount.class);
        when(account.getId()).thenReturn(accountId);

        ProfessorProfile professor = mock(ProfessorProfile.class);
        when(professor.getAccount()).thenReturn(account);

        Subject subject = mock(Subject.class);
        when(subject.getCode()).thenReturn("CS101");
        when(subject.getName()).thenReturn("Computer Science");

        SubjectOffering offering = mock(SubjectOffering.class);
        when(offering.getSubject()).thenReturn(subject);

        ProfessorAssignment assignment = mock(ProfessorAssignment.class);
        when(assignment.getProfessor()).thenReturn(professor);
        when(assignment.getOffering()).thenReturn(offering);

        ClassSession session = mock(ClassSession.class);
        when(session.getId()).thenReturn(42L);
        when(session.getAssignment()).thenReturn(assignment);
        when(session.getSessionDate()).thenReturn(LocalDate.of(2026, 11, 11));
        when(session.getStartTime()).thenReturn(LocalTime.of(9, 0));
        when(session.getEndTime()).thenReturn(LocalTime.of(10, 0));
        when(session.getStatus()).thenReturn("SCHEDULED");
        return session;
    }
}

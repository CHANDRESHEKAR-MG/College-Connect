package com.collegeconnect;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.collegeconnect.dto.AttendanceGroupSummary;
import com.collegeconnect.entity.AttendanceRecord;
import com.collegeconnect.entity.ClassSession;
import com.collegeconnect.entity.ProfessorAssignment;
import com.collegeconnect.entity.Section;
import com.collegeconnect.entity.Subject;
import com.collegeconnect.entity.SubjectOffering;
import com.collegeconnect.entity.StudentProfile;
import com.collegeconnect.repository.AttendanceRecordRepository;
import com.collegeconnect.repository.ClassSessionRepository;
import com.collegeconnect.repository.ParentProfileRepository;
import com.collegeconnect.repository.ParentStudentLinkRepository;
import com.collegeconnect.repository.ProfessorAssignmentRepository;
import com.collegeconnect.repository.StudentProfileRepository;
import com.collegeconnect.security.Role;
import com.collegeconnect.service.AttendanceRangeReportService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

class AttendanceRangeReportServiceTest {

    private AttendanceRecordRepository attendanceRecordRepository;
    private StudentProfileRepository studentProfileRepository;
    private AttendanceRangeReportService service;

    @BeforeEach
    void setUp() {
        attendanceRecordRepository = mock(AttendanceRecordRepository.class);
        studentProfileRepository = mock(StudentProfileRepository.class);
        service = new AttendanceRangeReportService(
                attendanceRecordRepository,
                studentProfileRepository,
                mock(ParentProfileRepository.class),
                mock(ParentStudentLinkRepository.class),
                mock(ProfessorAssignmentRepository.class),
                mock(ClassSessionRepository.class));
    }

    @Test
    void studentCannotRequestAnotherStudentsAttendanceReport() {
        StudentProfile ownStudent = mock(StudentProfile.class);
        when(ownStudent.getId()).thenReturn(11L);
        when(studentProfileRepository.findByAccount_Id(100L)).thenReturn(Optional.of(ownStudent));

        assertThatThrownBy(() -> service.report(
                        Role.STUDENT,
                        100L,
                        12L,
                        null,
                        null,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 31)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getStatusCode().value()).isEqualTo(403));

        verify(attendanceRecordRepository, never()).findDateRangeReport(
                any(), any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void rejectsReversedDateRangeBeforeQuerying() {
        assertThatThrownBy(() -> service.report(
                        Role.ADMIN,
                        1L,
                        null,
                        null,
                        null,
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 1, 1)))
                .isInstanceOf(ResponseStatusException.class);

        verify(attendanceRecordRepository, never()).findDateRangeReport(
                any(), any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void professorRowsAreConstrainedByAuthenticatedAccountId() {
        when(attendanceRecordRepository.findDateRangeReport(
                        any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        service.report(
                Role.PROFESSOR,
                200L,
                null,
                null,
                null,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31));

        verify(attendanceRecordRepository).findDateRangeReport(
                any(),
                any(),
                isNull(),
                isNull(),
                isNull(),
                org.mockito.ArgumentMatchers.eq(200L),
                any(Pageable.class));
    }

    @Test
    void subjectSummaryCountsPresentAbsentAndAuthorizedLeaveWithoutEligibilityThreshold() {
        LocalDate date = LocalDate.of(2026, 1, 12);
        List<AttendanceRecord> records = List.of(
                reportRecord("PRESENT", date),
                reportRecord("ABSENT", date),
                reportRecord("AUTHORIZED_LEAVE", date));
        when(attendanceRecordRepository.findDateRangeReport(
                        any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(records);

        List<AttendanceGroupSummary> summaries = service.summary(
                Role.ADMIN,
                1L,
                null,
                null,
                date,
                date,
                "subject");

        org.assertj.core.api.Assertions.assertThat(summaries).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(summaries.get(0).present()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(summaries.get(0).absent()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(summaries.get(0).authorizedLeave()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(summaries.get(0).attendancePercentage())
                .isEqualByComparingTo("50.00");
    }

    private AttendanceRecord reportRecord(String status, LocalDate date) {
        Subject subject = mock(Subject.class);
        when(subject.getCode()).thenReturn("CS101");
        when(subject.getName()).thenReturn("Programming");
        Section section = mock(Section.class);
        when(section.getId()).thenReturn(7L);
        when(section.getName()).thenReturn("A");
        SubjectOffering offering = mock(SubjectOffering.class);
        when(offering.getId()).thenReturn(8L);
        when(offering.getSection()).thenReturn(section);
        when(offering.getSubject()).thenReturn(subject);
        ProfessorAssignment assignment = mock(ProfessorAssignment.class);
        when(assignment.getOffering()).thenReturn(offering);
        ClassSession session = mock(ClassSession.class);
        when(session.getAssignment()).thenReturn(assignment);
        when(session.getSessionDate()).thenReturn(date);
        when(session.getStartTime()).thenReturn(LocalTime.of(9, 0));
        when(session.getEndTime()).thenReturn(LocalTime.of(10, 0));
        StudentProfile student = mock(StudentProfile.class);
        when(student.getId()).thenReturn(9L);
        when(student.getCollegeIdentifier()).thenReturn("STU9");
        when(student.getDisplayName()).thenReturn("Student");
        AttendanceRecord record = mock(AttendanceRecord.class);
        when(record.getId()).thenReturn((long) (status.hashCode() & 0x7fffffff));
        when(record.getSession()).thenReturn(session);
        when(record.getStudent()).thenReturn(student);
        when(record.getStatus()).thenReturn(status);
        return record;
    }
}

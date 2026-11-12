package com.collegeconnect.service;

import com.collegeconnect.dto.AttendanceRosterResponse;
import com.collegeconnect.dto.AttendanceRosterStudentResponse;
import com.collegeconnect.entity.ClassSession;
import com.collegeconnect.entity.SessionRoster;
import com.collegeconnect.repository.ClassSessionRepository;
import com.collegeconnect.repository.LeaveRequestRepository;
import com.collegeconnect.repository.SessionRosterRepository;
import com.collegeconnect.security.Role;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AttendanceRosterService {

    private final ClassSessionRepository classSessionRepository;
    private final SessionRosterRepository sessionRosterRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public AttendanceRosterService(
            ClassSessionRepository classSessionRepository,
            SessionRosterRepository sessionRosterRepository,
            LeaveRequestRepository leaveRequestRepository) {
        this.classSessionRepository = classSessionRepository;
        this.sessionRosterRepository = sessionRosterRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    @Transactional(readOnly = true)
    public AttendanceRosterResponse getRoster(Long sessionId, Long accountId, Role role) {
        if (role != Role.ADMIN && role != Role.PROFESSOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class session not found"));

        if (role == Role.PROFESSOR) {
            boolean isAssignedProfessor =
                    Objects.equals(session.getAssignment().getProfessor().getAccount().getId(), accountId);
            boolean isSubstituteProfessor = session.getSubstituteProfessor() != null
                    && Objects.equals(session.getSubstituteProfessor().getAccount().getId(), accountId);
            if (!isAssignedProfessor && !isSubstituteProfessor) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
            }
        }

        List<SessionRoster> rosterEntries =
                sessionRosterRepository.findBySession_IdOrderByStudent_CollegeIdentifierAsc(sessionId);
        List<Long> studentIds = rosterEntries.stream().map(entry -> entry.getStudent().getId()).toList();
        Set<Long> studentsWithApprovedLeave = studentIds.isEmpty()
                ? Set.of()
                : leaveRequestRepository.findApprovedForStudentsOnDate(studentIds, session.getSessionDate()).stream()
                        .map(leave -> leave.getStudent().getId())
                        .collect(Collectors.toSet());
        List<AttendanceRosterStudentResponse> students = rosterEntries.stream()
                .map(rosterEntry -> new AttendanceRosterStudentResponse(
                        rosterEntry.getStudent().getId(),
                        rosterEntry.getStudent().getCollegeIdentifier(),
                        rosterEntry.getStudent().getDisplayName(),
                        studentsWithApprovedLeave.contains(rosterEntry.getStudent().getId())))
                .toList();

        return new AttendanceRosterResponse(
                session.getId(),
                session.getSessionDate(),
                session.getStartTime(),
                session.getEndTime(),
                session.getStatus(),
                session.getAssignment().getOffering().getSubject().getCode(),
                session.getAssignment().getOffering().getSubject().getName(),
                students);
    }
}

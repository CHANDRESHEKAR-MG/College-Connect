package com.collegeconnect.service;

import com.collegeconnect.dto.AttendanceMarkRequest;
import com.collegeconnect.dto.AttendanceSubmissionRequest;
import com.collegeconnect.dto.AttendanceSubmissionResponse;
import com.collegeconnect.entity.AbsenceCase;
import com.collegeconnect.entity.AuditLog;
import com.collegeconnect.entity.AttendanceRecord;
import com.collegeconnect.entity.ClassSession;
import com.collegeconnect.entity.LeaveRequest;
import com.collegeconnect.entity.NotificationOutbox;
import com.collegeconnect.entity.NotificationPreference;
import com.collegeconnect.entity.ParentProfile;
import com.collegeconnect.entity.ParentStudentLink;
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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AttendanceSubmissionService {

    private static final String ABSENT = "ABSENT";
    private static final String ABSENT_PREFERENCE = "ATTENDANCE_ABSENT";

    private final ClassSessionRepository classSessionRepository;
    private final SessionRosterRepository sessionRosterRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ProfessorProfileRepository professorProfileRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AbsenceCaseRepository absenceCaseRepository;
    private final ParentStudentLinkRepository parentStudentLinkRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final NotificationOutboxRepository notificationOutboxRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuditLogRepository auditLogRepository;

    public AttendanceSubmissionService(
            ClassSessionRepository classSessionRepository,
            SessionRosterRepository sessionRosterRepository,
            LeaveRequestRepository leaveRequestRepository,
            ProfessorProfileRepository professorProfileRepository,
            AttendanceRecordRepository attendanceRecordRepository,
            AbsenceCaseRepository absenceCaseRepository,
            ParentStudentLinkRepository parentStudentLinkRepository,
            NotificationPreferenceRepository notificationPreferenceRepository,
            NotificationOutboxRepository notificationOutboxRepository,
            UserAccountRepository userAccountRepository,
            AuditLogRepository auditLogRepository) {
        this.classSessionRepository = classSessionRepository;
        this.sessionRosterRepository = sessionRosterRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.professorProfileRepository = professorProfileRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.absenceCaseRepository = absenceCaseRepository;
        this.parentStudentLinkRepository = parentStudentLinkRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.notificationOutboxRepository = notificationOutboxRepository;
        this.userAccountRepository = userAccountRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public AttendanceSubmissionResponse submit(
            Long sessionId, Long accountId, Role role, AttendanceSubmissionRequest request) {
        if (role != Role.PROFESSOR && role != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        ClassSession session = classSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class session not found"));
        if (role == Role.PROFESSOR) {
            ensureAssignedProfessor(session, accountId);
        }
        if ("SUBMITTED".equalsIgnoreCase(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Attendance has already been submitted");
        }
        if ("CANCELLED".equalsIgnoreCase(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled sessions cannot take attendance");
        }

        List<SessionRoster> roster = sessionRosterRepository
                .findBySession_IdOrderByStudent_CollegeIdentifierAsc(sessionId);
        if (roster.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session roster is empty");
        }

        Map<Long, StudentProfile> rosterStudents = new LinkedHashMap<>();
        for (SessionRoster rosterEntry : roster) {
            rosterStudents.put(rosterEntry.getStudent().getId(), rosterEntry.getStudent());
        }
        Set<Long> studentsWithApprovedLeave = leaveRequestRepository
                .findApprovedForStudentsOnDate(List.copyOf(rosterStudents.keySet()), session.getSessionDate())
                .stream()
                .map(LeaveRequest::getStudent)
                .map(StudentProfile::getId)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, String> submittedStatuses =
                validateSubmission(request, rosterStudents.keySet(), studentsWithApprovedLeave);
        ProfessorProfile submittingProfessor = role == Role.PROFESSOR
                ? professorProfileRepository.findByAccount_Id(accountId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.FORBIDDEN, "Professor profile not found"))
                : session.getAssignment().getProfessor();
        String sourceAction = role == Role.ADMIN ? "ADMIN_OVERRIDE" : "PROFESSOR_SUBMIT";

        Instant submittedAt = Instant.now();
        List<AttendanceRecord> records = rosterStudents.entrySet().stream()
                .map(entry -> new AttendanceRecord(
                        session,
                        entry.getValue(),
                        submittingProfessor,
                        submittedStatuses.get(entry.getKey()),
                        sourceAction,
                        submittedAt))
                .toList();
        records = attendanceRecordRepository.saveAllAndFlush(records);

        List<AbsenceCase> absenceCases = records.stream()
                .filter(record -> ABSENT.equals(record.getStatus()))
                .map(record -> new AbsenceCase(record, "UNEXPLAINED", submittedAt))
                .toList();
        absenceCaseRepository.saveAll(absenceCases);

        List<NotificationOutbox> notificationEvents = records.stream()
                .filter(record -> ABSENT.equals(record.getStatus()))
                .flatMap(record -> createAbsenceEvents(record, session, submittedAt).stream())
                .toList();
        notificationOutboxRepository.saveAll(notificationEvents);

        session.markSubmitted();
        if (role == Role.ADMIN) {
            UserAccount admin = userAccountRepository.findById(accountId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            auditLogRepository.save(new AuditLog(
                    admin,
                    Role.ADMIN.name(),
                    "ATTENDANCE_ADMIN_OVERRIDE",
                    "CLASS_SESSION",
                    sessionId.toString(),
                    "Administrative attendance submission",
                    submittedAt));
        }
        return new AttendanceSubmissionResponse(sessionId, "SUBMITTED", submittedAt, records.size());
    }

    private void ensureAssignedProfessor(ClassSession session, Long accountId) {
        boolean assigned = Objects.equals(
                session.getAssignment().getProfessor().getAccount().getId(), accountId);
        boolean substitute = session.getSubstituteProfessor() != null
                && Objects.equals(session.getSubstituteProfessor().getAccount().getId(), accountId);
        if (!assigned && !substitute) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    private Map<Long, String> validateSubmission(
            AttendanceSubmissionRequest request,
            Set<Long> rosterStudentIds,
            Set<Long> studentsWithApprovedLeave) {
        Map<Long, String> submittedStatuses = new LinkedHashMap<>();
        for (AttendanceMarkRequest mark : request.records()) {
            String status = mark.status().toUpperCase(Locale.ROOT);
            if (!"PRESENT".equals(status) && !ABSENT.equals(status) && !"AUTHORIZED_LEAVE".equals(status)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported attendance status");
            }
            if (!rosterStudentIds.contains(mark.studentId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Student is not on this session roster");
            }
            boolean hasApprovedLeave = studentsWithApprovedLeave.contains(mark.studentId());
            if ("AUTHORIZED_LEAVE".equals(status) && !hasApprovedLeave) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Authorized leave requires approved leave for this session date");
            }
            if (ABSENT.equals(status) && hasApprovedLeave) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "A student with approved leave cannot be submitted as absent; use AUTHORIZED_LEAVE");
            }
            if (submittedStatuses.putIfAbsent(mark.studentId(), status) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate student in attendance submission");
            }
        }
        if (!submittedStatuses.keySet().equals(rosterStudentIds)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attendance must include the complete roster");
        }
        return submittedStatuses;
    }

    private List<NotificationOutbox> createAbsenceEvents(
            AttendanceRecord record, ClassSession session, Instant createdAt) {
        List<ParentStudentLink> activeLinks = parentStudentLinkRepository
                .findByStudent_Id(record.getStudent().getId()).stream()
                .filter(ParentStudentLink::isActiveVerified)
                .toList();

        if (activeLinks.isEmpty()) {
            return List.of(createOutboxEvent(record, session, null, createdAt, "SKIPPED_NO_OPTIN"));
        }

        return activeLinks.stream()
                .map(link -> {
                    ParentProfile parent = link.getParent();
                    String status = getNotificationStatus(parent);
                    return createOutboxEvent(record, session, parent, createdAt, status);
                })
                .toList();
    }

    private String getNotificationStatus(ParentProfile parent) {
        if (!parent.hasVerifiedMobile() || !parent.isWhatsappOptIn()) {
            return "SKIPPED_NO_OPTIN";
        }
        NotificationPreference preference = notificationPreferenceRepository
                .findByParent_IdAndCategory(parent.getId(), ABSENT_PREFERENCE)
                .orElse(null);
        return preference != null && !preference.isEnabled() ? "SKIPPED_PREFERENCE" : "PENDING";
    }

    private NotificationOutbox createOutboxEvent(
            AttendanceRecord record,
            ClassSession session,
            ParentProfile parent,
            Instant createdAt,
            String status) {
        Long parentId = parent == null ? null : parent.getId();
        String idempotencyKey = "attendance:" + session.getId() + ":" + record.getStudent().getId()
                + ":" + (parentId == null ? "none" : parentId) + ":ABSENT";
        String payload = "{\"sessionId\":" + session.getId()
                + ",\"studentId\":" + record.getStudent().getId()
                + ",\"attendanceId\":" + record.getId()
                + ",\"status\":\"ABSENT\"}";
        return new NotificationOutbox(
                record.getStudent(),
                parent,
                "ATTENDANCE",
                record.getId(),
                "TPL-ATT-ABSENT",
                payload,
                idempotencyKey,
                status,
                createdAt);
    }
}

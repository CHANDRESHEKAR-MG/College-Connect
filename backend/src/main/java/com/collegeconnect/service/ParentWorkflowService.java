package com.collegeconnect.service;

import com.collegeconnect.dto.AbsenceCaseResponse;
import com.collegeconnect.dto.AbsenceExplanationRequest;
import com.collegeconnect.dto.AbsenceReviewRequest;
import com.collegeconnect.dto.LeaveRequestCreateRequest;
import com.collegeconnect.dto.LeaveRequestResponse;
import com.collegeconnect.dto.LeaveReviewRequest;
import com.collegeconnect.entity.AbsenceCase;
import com.collegeconnect.entity.LeaveRequest;
import com.collegeconnect.entity.NotificationOutbox;
import com.collegeconnect.entity.NotificationPreference;
import com.collegeconnect.entity.ParentProfile;
import com.collegeconnect.entity.ParentStudentLink;
import com.collegeconnect.entity.StudentProfile;
import com.collegeconnect.entity.UserAccount;
import com.collegeconnect.repository.AbsenceCaseRepository;
import com.collegeconnect.repository.ClassSessionRepository;
import com.collegeconnect.repository.LeaveRequestRepository;
import com.collegeconnect.repository.ParentProfileRepository;
import com.collegeconnect.repository.ParentStudentLinkRepository;
import com.collegeconnect.repository.NotificationOutboxRepository;
import com.collegeconnect.repository.NotificationPreferenceRepository;
import com.collegeconnect.repository.StudentProfileRepository;
import com.collegeconnect.repository.UserAccountRepository;
import com.collegeconnect.security.Role;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ParentWorkflowService {

    private static final List<String> ACTIVE_LEAVE_STATUSES = List.of("SUBMITTED", "UNDER_REVIEW", "APPROVED");

    private final AbsenceCaseRepository absenceCaseRepository;
    private final ClassSessionRepository classSessionRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ParentProfileRepository parentProfileRepository;
    private final ParentStudentLinkRepository parentStudentLinkRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final UserAccountRepository userAccountRepository;
    private final NotificationOutboxRepository notificationOutboxRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;

    public ParentWorkflowService(
            AbsenceCaseRepository absenceCaseRepository,
            ClassSessionRepository classSessionRepository,
            LeaveRequestRepository leaveRequestRepository,
            ParentProfileRepository parentProfileRepository,
            ParentStudentLinkRepository parentStudentLinkRepository,
            StudentProfileRepository studentProfileRepository,
            UserAccountRepository userAccountRepository,
            NotificationOutboxRepository notificationOutboxRepository,
            NotificationPreferenceRepository notificationPreferenceRepository) {
        this.absenceCaseRepository = absenceCaseRepository;
        this.classSessionRepository = classSessionRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.parentProfileRepository = parentProfileRepository;
        this.parentStudentLinkRepository = parentStudentLinkRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.userAccountRepository = userAccountRepository;
        this.notificationOutboxRepository = notificationOutboxRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
    }

    @Transactional(readOnly = true)
    public List<AbsenceCaseResponse> listParentAbsences(Long accountId, Long studentId) {
        ParentProfile parent = getParentProfile(accountId);
        requireActiveVerifiedLink(parent.getId(), studentId);
        return absenceCaseRepository.findByAttendance_Student_IdOrderByCreatedAtDesc(studentId).stream()
                .map(this::toAbsenceResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AbsenceCaseResponse> listStudentAbsences(Long accountId) {
        StudentProfile student = studentProfileRepository.findByAccount_Id(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Student profile not found"));
        return absenceCaseRepository.findByAttendance_Student_IdOrderByCreatedAtDesc(student.getId()).stream()
                .map(this::toAbsenceResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AbsenceCaseResponse> listProfessorAbsences(Long accountId, Role role) {
        requireRole(role, Role.PROFESSOR);
        return absenceCaseRepository.findAssignedToProfessor(accountId).stream()
                .map(this::toAbsenceResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AbsenceCaseResponse> listAdminAbsences(Long accountId, Role role) {
        requireRole(role, Role.ADMIN);
        getAccount(accountId);
        return absenceCaseRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toAbsenceResponse)
                .toList();
    }

    @Transactional
    public AbsenceCaseResponse explainAbsence(
            Long accountId, Long caseId, AbsenceExplanationRequest request) {
        ParentProfile parent = getParentProfile(accountId);
        AbsenceCase absenceCase = absenceCaseRepository.findByIdForUpdate(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Absence case not found"));
        requireActiveVerifiedLink(parent.getId(), absenceCase.getAttendance().getStudent().getId());
        if (!"UNEXPLAINED".equalsIgnoreCase(absenceCase.getStatus())
                && !"MORE_INFO".equalsIgnoreCase(absenceCase.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This absence case is not accepting an explanation");
        }
        absenceCase.submitExplanation(request.explanation().trim(), Instant.now());
        return toAbsenceResponse(absenceCase);
    }

    @Transactional
    public AbsenceCaseResponse reviewAbsence(Long accountId, Long caseId, AbsenceReviewRequest request) {
        AbsenceCase absenceCase = absenceCaseRepository.findByIdForUpdate(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Absence case not found"));
        if (!"UNDER_REVIEW".equalsIgnoreCase(absenceCase.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only cases under review can be decided");
        }
        UserAccount reviewer = getAccount(accountId);
        String decision = request.decision().toUpperCase(Locale.ROOT);
        String status = "MORE_INFO".equals(decision) ? "MORE_INFO" : decision;
        absenceCase.review(reviewer, status, trimToNull(request.comments()), Instant.now());
        createAbsenceDecisionEvents(absenceCase, status, absenceCase.getUpdatedAt());
        return toAbsenceResponse(absenceCase);
    }

    @Transactional
    public LeaveRequestResponse createLeave(Long accountId, LeaveRequestCreateRequest request) {
        ParentProfile parent = getParentProfile(accountId);
        requireActiveVerifiedLink(parent.getId(), request.studentId());
        if (request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date must be on or after start date");
        }
        LocalDate today = LocalDate.now();
        if (request.startDate().isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Leave cannot be requested for a past date");
        }
        StudentProfile student = studentProfileRepository.findByIdForUpdate(request.studentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
        if (classSessionRepository.hasStartedSessionInSectionAndDateRange(
                student.getSection().getId(),
                request.startDate(),
                request.endDate(),
                today,
                LocalTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Leave must be requested before affected class sessions start");
        }
        List<LeaveRequest> overlaps = leaveRequestRepository.findByStudent_IdAndStatusIn(
                request.studentId(), ACTIVE_LEAVE_STATUSES);
        boolean overlapsExisting = overlaps.stream().anyMatch(existing ->
                !request.endDate().isBefore(existing.getStartDate())
                        && !request.startDate().isAfter(existing.getEndDate()));
        if (overlapsExisting) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An active leave request overlaps these dates");
        }

        UserAccount requester = getAccount(accountId);
        LeaveRequest leave = leaveRequestRepository.save(new LeaveRequest(
                student,
                requester,
                request.startDate(),
                request.endDate(),
                request.reasonCategory(),
                request.reason().trim(),
                "SUBMITTED",
                Instant.now()));
        return toLeaveResponse(leave);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> listParentLeave(Long accountId) {
        ParentProfile parent = getParentProfile(accountId);
        return leaveRequestRepository.findByRequester_IdOrderBySubmittedAtDesc(accountId).stream()
                .filter(leave -> parentStudentLinkRepository
                        .findByParent_IdAndStudent_Id(parent.getId(), leave.getStudent().getId()).stream()
                        .anyMatch(ParentStudentLink::isActiveVerified))
                .map(this::toLeaveResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> listProfessorLeave(Long accountId, Role role) {
        requireRole(role, Role.PROFESSOR);
        return leaveRequestRepository.findAssignedToProfessor(accountId).stream()
                .map(this::toLeaveResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> listAdminLeave(Long accountId, Role role) {
        requireRole(role, Role.ADMIN);
        getAccount(accountId);
        return leaveRequestRepository.findAllByOrderBySubmittedAtDesc().stream()
                .map(this::toLeaveResponse)
                .toList();
    }

    @Transactional
    public LeaveRequestResponse cancelLeave(Long accountId, Long requestId) {
        LeaveRequest leave = leaveRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave request not found"));
        if (!leave.getRequester().getId().equals(accountId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave request not found");
        }
        ParentProfile parent = getParentProfile(accountId);
        requireActiveVerifiedLink(parent.getId(), leave.getStudent().getId());
        if (!"SUBMITTED".equalsIgnoreCase(leave.getStatus())
                && !"UNDER_REVIEW".equalsIgnoreCase(leave.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This leave request can no longer be cancelled");
        }
        leave.cancel();
        return toLeaveResponse(leave);
    }

    @Transactional
    public LeaveRequestResponse reviewLeave(Long accountId, Long requestId, LeaveReviewRequest request) {
        LeaveRequest leave = leaveRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave request not found"));
        if (!"SUBMITTED".equalsIgnoreCase(leave.getStatus())
                && !"UNDER_REVIEW".equalsIgnoreCase(leave.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This leave request has already been decided");
        }
        UserAccount reviewer = getAccount(accountId);
        leave.review(
                reviewer,
                request.decision().toUpperCase(Locale.ROOT),
                trimToNull(request.comment()),
                Instant.now());
        if ("APPROVED".equalsIgnoreCase(leave.getStatus())
                || "REJECTED".equalsIgnoreCase(leave.getStatus())) {
            createLeaveDecisionEvent(leave, leave.getStatus(), leave.getReviewedAt());
        }
        return toLeaveResponse(leave);
    }

    private ParentProfile getParentProfile(Long accountId) {
        return parentProfileRepository.findByAccount_Id(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Parent profile not found"));
    }

    private UserAccount getAccount(Long accountId) {
        return userAccountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void requireActiveVerifiedLink(Long parentId, Long studentId) {
        boolean linked = parentStudentLinkRepository.findByParent_IdAndStudent_Id(parentId, studentId).stream()
                .anyMatch(ParentStudentLink::isActiveVerified);
        if (!linked) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No active verified link to this student");
        }
    }

    private void requireRole(Role actual, Role expected) {
        if (actual != expected) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    private AbsenceCaseResponse toAbsenceResponse(AbsenceCase absenceCase) {
        return new AbsenceCaseResponse(
                absenceCase.getId(),
                absenceCase.getAttendance().getId(),
                absenceCase.getAttendance().getStudent().getId(),
                absenceCase.getAttendance().getSession().getId(),
                absenceCase.getStatus(),
                absenceCase.getExplanation(),
                absenceCase.getReviewComments(),
                absenceCase.getCreatedAt(),
                absenceCase.getUpdatedAt());
    }

    private LeaveRequestResponse toLeaveResponse(LeaveRequest leave) {
        return new LeaveRequestResponse(
                leave.getId(),
                leave.getStudent().getId(),
                leave.getStudent().getDisplayName(),
                leave.getRequester().getId(),
                leave.getStartDate(),
                leave.getEndDate(),
                leave.getReasonCategory(),
                leave.getReason(),
                leave.getStatus(),
                leave.getReviewComment(),
                leave.getSubmittedAt(),
                leave.getReviewedAt());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void createAbsenceDecisionEvents(AbsenceCase absenceCase, String decision, Instant createdAt) {
        Long studentId = absenceCase.getAttendance().getStudent().getId();
        List<NotificationOutbox> events = parentStudentLinkRepository.findByStudent_Id(studentId).stream()
                .filter(ParentStudentLink::isActiveVerified)
                .map(link -> {
                    ParentProfile parent = link.getParent();
                    String status = getNotificationStatus(parent, "ABSENCE");
                    String key = "absence-decision:" + absenceCase.getId() + ":" + parent.getId()
                            + ":" + createdAt.toEpochMilli();
                    return new NotificationOutbox(
                            absenceCase.getAttendance().getStudent(),
                            parent,
                            "ABSENCE_DECISION",
                            absenceCase.getId(),
                            "TPL-ABS-DECISION",
                            "{\"absenceCaseId\":" + absenceCase.getId() + ",\"decision\":\"" + decision + "\"}",
                            key,
                            status,
                            createdAt);
                })
                .toList();
        notificationOutboxRepository.saveAll(events);
    }

    private void createLeaveDecisionEvent(LeaveRequest leave, String decision, Instant createdAt) {
        ParentProfile parent = parentProfileRepository.findByAccount_Id(leave.getRequester().getId()).orElse(null);
        if (parent == null || parentStudentLinkRepository
                .findByParent_IdAndStudent_Id(parent.getId(), leave.getStudent().getId()).stream()
                .noneMatch(ParentStudentLink::isActiveVerified)) {
            return;
        }
        String key = "leave-decision:" + leave.getId() + ":" + createdAt.toEpochMilli();
        NotificationOutbox event = new NotificationOutbox(
                leave.getStudent(),
                parent,
                "LEAVE_DECISION",
                leave.getId(),
                "TPL-LVE-DECISION",
                "{\"leaveRequestId\":" + leave.getId() + ",\"decision\":\"" + decision + "\"}",
                key,
                getNotificationStatus(parent, "LEAVE"),
                createdAt);
        notificationOutboxRepository.save(event);
    }

    private String getNotificationStatus(ParentProfile parent, String category) {
        if (!parent.hasVerifiedMobile() || !parent.isWhatsappOptIn()) {
            return "SKIPPED_NO_OPTIN";
        }
        NotificationPreference preference = notificationPreferenceRepository
                .findByParent_IdAndCategory(parent.getId(), category)
                .orElse(null);
        return preference != null && !preference.isEnabled() ? "SKIPPED_PREFERENCE" : "PENDING";
    }
}

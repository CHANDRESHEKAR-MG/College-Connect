package com.collegeconnect.service;

import com.collegeconnect.dto.AttendanceCorrectionHistoryResponse;
import com.collegeconnect.dto.AttendanceCorrectionRequest;
import com.collegeconnect.dto.AttendanceCorrectionResponse;
import com.collegeconnect.entity.AbsenceCase;
import com.collegeconnect.entity.AttendanceCorrection;
import com.collegeconnect.entity.AttendanceRecord;
import com.collegeconnect.entity.ClassSession;
import com.collegeconnect.entity.NotificationOutbox;
import com.collegeconnect.entity.ParentProfile;
import com.collegeconnect.entity.ParentStudentLink;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AttendanceCorrectionService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AttendanceCorrectionRepository attendanceCorrectionRepository;
    private final AbsenceCaseRepository absenceCaseRepository;
    private final ParentStudentLinkRepository parentStudentLinkRepository;
    private final NotificationOutboxRepository notificationOutboxRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final UserAccountRepository userAccountRepository;
    private final boolean professorCorrectionsEnabled;
    private final Duration professorCorrectionWindow;

    public AttendanceCorrectionService(
            AttendanceRecordRepository attendanceRecordRepository,
            LeaveRequestRepository leaveRequestRepository,
            AttendanceCorrectionRepository attendanceCorrectionRepository,
            AbsenceCaseRepository absenceCaseRepository,
            ParentStudentLinkRepository parentStudentLinkRepository,
            NotificationOutboxRepository notificationOutboxRepository,
            NotificationPreferenceRepository notificationPreferenceRepository,
            UserAccountRepository userAccountRepository,
            @Value("${collegeconnect.attendance.correction.professor-enabled:false}")
                    boolean professorCorrectionsEnabled,
            @Value("${collegeconnect.attendance.correction.professor-window:PT0S}")
                    Duration professorCorrectionWindow) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.attendanceCorrectionRepository = attendanceCorrectionRepository;
        this.absenceCaseRepository = absenceCaseRepository;
        this.parentStudentLinkRepository = parentStudentLinkRepository;
        this.notificationOutboxRepository = notificationOutboxRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.userAccountRepository = userAccountRepository;
        this.professorCorrectionsEnabled = professorCorrectionsEnabled;
        this.professorCorrectionWindow = professorCorrectionWindow;
    }

    @Transactional
    public AttendanceCorrectionResponse correct(
            Long attendanceId,
            Long actorAccountId,
            Role actorRole,
            AttendanceCorrectionRequest request) {
        if (actorRole != Role.ADMIN && actorRole != Role.PROFESSOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        if (actorRole == Role.PROFESSOR
                && (!professorCorrectionsEnabled
                        || professorCorrectionWindow.isZero()
                        || professorCorrectionWindow.isNegative())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Professor corrections are disabled until correction policy is configured");
        }

        AttendanceRecord attendance = attendanceRecordRepository.findByIdForUpdate(attendanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attendance record not found"));
        if (attendance.getVersion() != request.expectedVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Attendance was changed; reload before correcting");
        }
        if (!"SUBMITTED".equalsIgnoreCase(attendance.getSession().getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only submitted attendance can be corrected");
        }
        if (actorRole == Role.PROFESSOR
                && Instant.now().isAfter(attendance.getSubmittedAt().plus(professorCorrectionWindow))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Professor correction window has expired");
        }
        if (actorRole == Role.PROFESSOR && !isAssignedProfessor(attendance.getSession(), actorAccountId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        String oldStatus = attendance.getStatus();
        String newStatus = request.newStatus().toUpperCase(java.util.Locale.ROOT);
        if (Objects.equals(oldStatus, newStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Correction must change the attendance status");
        }
        if ("AUTHORIZED_LEAVE".equals(newStatus)
                && !"ABSENT".equals(oldStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Only an absent record can be corrected to authorized leave");
        }
        if ("AUTHORIZED_LEAVE".equals(newStatus)
                && leaveRequestRepository.findApprovedForStudentsOnDate(
                                List.of(attendance.getStudent().getId()), attendance.getSession().getSessionDate())
                        .isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Authorized leave requires approved leave for the session date");
        }

        UserAccount actor = userAccountRepository.findById(actorAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        int sequence = attendanceCorrectionRepository.findHighestSequence(attendanceId) + 1;
        Instant correctedAt = Instant.now();
        attendance.correctStatus(newStatus);
        AttendanceCorrection correction = attendanceCorrectionRepository.saveAndFlush(new AttendanceCorrection(
                attendance,
                actor,
                actorRole.name(),
                sequence,
                oldStatus,
                newStatus,
                request.reason().trim(),
                request.comment() == null ? null : request.comment().trim(),
                correctedAt));

        if ("ABSENT".equals(oldStatus) && !"ABSENT".equals(newStatus)) {
            cancelUnacceptedAbsenceNotifications(attendanceId, correctedAt);
        }
        updateAbsenceCase(attendance, oldStatus, newStatus, correctedAt);
        createCorrectionOutboxEvents(attendance, correction, correctedAt);
        return new AttendanceCorrectionResponse(
                correction.getId(),
                attendanceId,
                oldStatus,
                newStatus,
                actor.getId(),
                actor.getLoginIdentifier(),
                actorRole.name(),
                correction.getReason(),
                correction.getComment(),
                sequence,
                correctedAt);
    }

    @Transactional(readOnly = true)
    public AttendanceCorrectionHistoryResponse getHistory(Long attendanceId, Long accountId, Role role) {
        AttendanceRecord attendance = attendanceRecordRepository.findById(attendanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attendance record not found"));
        if (!canReadHistory(attendance.getSession(), accountId, role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        List<AttendanceCorrectionResponse> corrections = attendanceCorrectionRepository
                .findByAttendance_IdOrderByCorrectionSequenceAsc(attendanceId).stream()
                .map(correction -> new AttendanceCorrectionResponse(
                        correction.getId(),
                        attendanceId,
                        correction.getPreviousStatus(),
                        correction.getNewStatus(),
                        correction.getActor().getId(),
                        correction.getActor().getLoginIdentifier(),
                        correction.getActorRole(),
                        correction.getReason(),
                        correction.getComment(),
                        correction.getCorrectionSequence(),
                        correction.getCreatedAt()))
                .toList();
        return new AttendanceCorrectionHistoryResponse(
                attendanceId,
                attendance.getStatus(),
                attendance.getSubmittedBy().getDisplayName(),
                attendance.getSubmittedAt(),
                corrections);
    }

    private boolean canReadHistory(ClassSession session, Long accountId, Role role) {
        return role == Role.ADMIN
                || role == Role.PROFESSOR && isAssignedProfessor(session, accountId);
    }

    private boolean isAssignedProfessor(ClassSession session, Long accountId) {
        return Objects.equals(session.getAssignment().getProfessor().getAccount().getId(), accountId)
                || session.getSubstituteProfessor() != null
                        && Objects.equals(session.getSubstituteProfessor().getAccount().getId(), accountId);
    }

    private void updateAbsenceCase(
            AttendanceRecord attendance, String oldStatus, String newStatus, Instant changedAt) {
        if ("ABSENT".equals(newStatus)) {
            AbsenceCase absenceCase = absenceCaseRepository.findByAttendance_Id(attendance.getId())
                    .orElseGet(() -> new AbsenceCase(attendance, "UNEXPLAINED", changedAt));
            absenceCase.reopenForCorrection(changedAt);
            absenceCaseRepository.save(absenceCase);
        } else if ("ABSENT".equals(oldStatus)) {
            absenceCaseRepository.findByAttendance_Id(attendance.getId())
                    .ifPresent(absenceCase -> absenceCase.closeForCorrection(changedAt));
        }
    }

    private void cancelUnacceptedAbsenceNotifications(Long attendanceId, Instant updatedAt) {
        notificationOutboxRepository.findBySourceTypeAndSourceIdForUpdate("ATTENDANCE", attendanceId)
                .forEach(event -> event.cancelIfNotAccepted(updatedAt));
    }

    private void createCorrectionOutboxEvents(
            AttendanceRecord attendance, AttendanceCorrection correction, Instant createdAt) {
        List<ParentStudentLink> links = parentStudentLinkRepository
                .findByStudent_Id(attendance.getStudent().getId()).stream()
                .filter(ParentStudentLink::isActiveVerified)
                .toList();
        if (links.isEmpty()) {
            notificationOutboxRepository.save(createOutbox(
                    attendance, correction, null, createdAt, "SKIPPED_NO_OPTIN"));
            return;
        }
        for (ParentStudentLink link : links) {
            ParentProfile parent = link.getParent();
            String status = "SKIPPED_NO_OPTIN";
            if (parent.hasVerifiedMobile() && parent.isWhatsappOptIn()) {
                boolean resultingStatusIsPresent = "PRESENT".equals(attendance.getStatus());
                String preferenceCategory =
                        resultingStatusIsPresent ? "ATTENDANCE_PRESENT" : "ATTENDANCE_ABSENT";
                var preference = notificationPreferenceRepository
                        .findByParent_IdAndCategory(parent.getId(), preferenceCategory)
                        .orElse(null);
                status = preference != null
                        ? preference.isEnabled() ? "PENDING" : "SKIPPED_PREFERENCE"
                        : resultingStatusIsPresent ? "SKIPPED_PREFERENCE" : "PENDING";
            }
            notificationOutboxRepository.save(createOutbox(attendance, correction, parent, createdAt, status));
        }
    }

    private NotificationOutbox createOutbox(
            AttendanceRecord attendance,
            AttendanceCorrection correction,
            ParentProfile parent,
            Instant createdAt,
            String status) {
        Long parentId = parent == null ? null : parent.getId();
        String key = "attendance-correction:" + correction.getId() + ":" + (parentId == null ? "none" : parentId);
        String payload = "{\"attendanceId\":" + attendance.getId()
                + ",\"studentId\":" + attendance.getStudent().getId()
                + ",\"correctionId\":" + correction.getId()
                + ",\"previousStatus\":\"" + correction.getPreviousStatus() + "\""
                + ",\"status\":\"" + attendance.getStatus() + "\"}";
        return new NotificationOutbox(
                attendance.getStudent(),
                parent,
                "ATTENDANCE_CORRECTION",
                correction.getId(),
                "TPL-COR",
                payload,
                key,
                status,
                createdAt);
    }
}

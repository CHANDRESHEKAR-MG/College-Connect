package com.collegeconnect.service;

import com.collegeconnect.dto.AssessmentCreateRequest;
import com.collegeconnect.dto.AssessmentResponse;
import com.collegeconnect.dto.MarkEntryRequest;
import com.collegeconnect.dto.PublishedMarkResponse;
import com.collegeconnect.dto.SeeAssessmentCreateRequest;
import com.collegeconnect.entity.CieAssessment;
import com.collegeconnect.entity.CieMark;
import com.collegeconnect.entity.CieMarkHistory;
import com.collegeconnect.entity.AuditLog;
import com.collegeconnect.entity.NotificationOutbox;
import com.collegeconnect.entity.NotificationPreference;
import com.collegeconnect.entity.ParentProfile;
import com.collegeconnect.entity.ParentStudentLink;
import com.collegeconnect.entity.SeeAssessment;
import com.collegeconnect.entity.SeeMark;
import com.collegeconnect.entity.SeeMarkHistory;
import com.collegeconnect.entity.StudentProfile;
import com.collegeconnect.entity.SubjectOffering;
import com.collegeconnect.entity.UserAccount;
import com.collegeconnect.repository.CieAssessmentRepository;
import com.collegeconnect.repository.CieMarkHistoryRepository;
import com.collegeconnect.repository.CieMarkRepository;
import com.collegeconnect.repository.AuditLogRepository;
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
import com.collegeconnect.security.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MarkManagementService {

    private final CieAssessmentRepository cieAssessmentRepository;
    private final CieMarkRepository cieMarkRepository;
    private final CieMarkHistoryRepository cieMarkHistoryRepository;
    private final SeeAssessmentRepository seeAssessmentRepository;
    private final SeeMarkRepository seeMarkRepository;
    private final SeeMarkHistoryRepository seeMarkHistoryRepository;
    private final SubjectOfferingRepository subjectOfferingRepository;
    private final ProfessorAssignmentRepository professorAssignmentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final ParentProfileRepository parentProfileRepository;
    private final ParentStudentLinkRepository parentStudentLinkRepository;
    private final UserAccountRepository userAccountRepository;
    private final NotificationOutboxRepository notificationOutboxRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final AuditLogRepository auditLogRepository;

    public MarkManagementService(
            CieAssessmentRepository cieAssessmentRepository,
            CieMarkRepository cieMarkRepository,
            CieMarkHistoryRepository cieMarkHistoryRepository,
            SeeAssessmentRepository seeAssessmentRepository,
            SeeMarkRepository seeMarkRepository,
            SeeMarkHistoryRepository seeMarkHistoryRepository,
            SubjectOfferingRepository subjectOfferingRepository,
            ProfessorAssignmentRepository professorAssignmentRepository,
            EnrollmentRepository enrollmentRepository,
            StudentProfileRepository studentProfileRepository,
            ParentProfileRepository parentProfileRepository,
            ParentStudentLinkRepository parentStudentLinkRepository,
            UserAccountRepository userAccountRepository,
            NotificationOutboxRepository notificationOutboxRepository,
            NotificationPreferenceRepository notificationPreferenceRepository,
            AuditLogRepository auditLogRepository) {
        this.cieAssessmentRepository = cieAssessmentRepository;
        this.cieMarkRepository = cieMarkRepository;
        this.cieMarkHistoryRepository = cieMarkHistoryRepository;
        this.seeAssessmentRepository = seeAssessmentRepository;
        this.seeMarkRepository = seeMarkRepository;
        this.seeMarkHistoryRepository = seeMarkHistoryRepository;
        this.subjectOfferingRepository = subjectOfferingRepository;
        this.professorAssignmentRepository = professorAssignmentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.parentProfileRepository = parentProfileRepository;
        this.parentStudentLinkRepository = parentStudentLinkRepository;
        this.userAccountRepository = userAccountRepository;
        this.notificationOutboxRepository = notificationOutboxRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public AssessmentResponse createCie(Role role, Long accountId, AssessmentCreateRequest request) {
        requireStaff(role);
        SubjectOffering offering = subjectOfferingRepository.findById(request.offeringId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offering not found"));
        ensureOfferingAccess(role, accountId, offering.getId());
        CieAssessment assessment = cieAssessmentRepository.save(new CieAssessment(
                offering, request.name().trim(), request.maximumMarks(), "DRAFT"));
        return toResponse(assessment);
    }

    @Transactional
    public AssessmentResponse createSee(Role role, Long accountId, SeeAssessmentCreateRequest request) {
        requireStaff(role);
        SubjectOffering offering = subjectOfferingRepository.findById(request.offeringId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offering not found"));
        ensureOfferingAccess(role, accountId, offering.getId());
        SeeAssessment assessment = seeAssessmentRepository.save(
                new SeeAssessment(offering, request.name().trim(), "DRAFT"));
        return toResponse(assessment);
    }

    @Transactional
    public void enterCieMark(Role role, Long accountId, Long assessmentId, MarkEntryRequest request) {
        requireStaff(role);
        CieAssessment assessment = cieAssessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CIE assessment not found"));
        ensureOfferingAccess(role, accountId, assessment.getOffering().getId());
        StudentProfile student = requireEnrolledStudent(request.studentId(), assessment.getOffering().getId());
        BigDecimal value;
        try {
            value = new BigDecimal(request.value());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CIE marks must be numeric");
        }
        if (value.signum() < 0 || value.compareTo(assessment.getMaximumMarks()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CIE marks must be between zero and maximum marks");
        }
        try {
            value = value.setScale(2, java.math.RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CIE marks support at most two decimal places");
        }
        UserAccount actor = getAccount(accountId);
        Instant changedAt = Instant.now();
        CieMark mark = cieMarkRepository.findByAssessment_IdAndStudent_Id(assessmentId, student.getId())
                .orElse(null);
        BigDecimal previous = mark == null ? null : mark.getMarks();
        int revision;
        if (mark == null) {
            mark = new CieMark(assessment, student, value);
            revision = 1;
            mark = cieMarkRepository.saveAndFlush(mark);
        } else {
            mark.revise(value);
            revision = mark.getRevisionNumber();
        }
        cieMarkHistoryRepository.save(new CieMarkHistory(
                mark, actor, revision, previous, value, request.reason().trim(), changedAt));
    }

    @Transactional
    public void enterSeeMark(Role role, Long accountId, Long assessmentId, MarkEntryRequest request) {
        requireStaff(role);
        SeeAssessment assessment = seeAssessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SEE assessment not found"));
        ensureOfferingAccess(role, accountId, assessment.getOffering().getId());
        StudentProfile student = requireEnrolledStudent(request.studentId(), assessment.getOffering().getId());
        String value = request.value().trim();
        if (value.matches("-?\\d+(\\.\\d+)?") && new BigDecimal(value).signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SEE results cannot be negative");
        }
        UserAccount actor = getAccount(accountId);
        Instant changedAt = Instant.now();
        SeeMark mark = seeMarkRepository.findByAssessment_IdAndStudent_Id(assessmentId, student.getId())
                .orElse(null);
        String previous = mark == null ? null : mark.getResultValue();
        int revision;
        if (mark == null) {
            mark = new SeeMark(assessment, student, value);
            revision = 1;
            mark = seeMarkRepository.saveAndFlush(mark);
        } else {
            mark.revise(value);
            revision = mark.getRevisionNumber();
        }
        seeMarkHistoryRepository.save(new SeeMarkHistory(
                mark, actor, revision, previous, value, request.reason().trim(), changedAt));
    }

    @Transactional
    public AssessmentResponse publishCie(Long assessmentId, Long adminAccountId) {
        CieAssessment assessment = cieAssessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CIE assessment not found"));
        List<CieMark> marks = cieMarkRepository.findByAssessment_Id(assessmentId);
        if (marks.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An assessment without marks cannot be published");
        }
        Instant publishedAt = Instant.now();
        marks.forEach(CieMark::publishCurrentRevision);
        assessment.publish(publishedAt);
        writePublishAudit(adminAccountId, "CIE_ASSESSMENT", assessmentId);
        createPublicationEvents(
                marks.stream().map(CieMark::getStudent).toList(),
                assessmentId,
                "CIE",
                assessment.getName(),
                "TPL-CIE-PUB",
                publishedAt);
        return toResponse(assessment);
    }

    @Transactional
    public AssessmentResponse publishSee(Long assessmentId, Long adminAccountId) {
        SeeAssessment assessment = seeAssessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SEE assessment not found"));
        List<SeeMark> marks = seeMarkRepository.findByAssessment_Id(assessmentId);
        if (marks.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An assessment without results cannot be published");
        }
        Instant publishedAt = Instant.now();
        marks.forEach(SeeMark::publishCurrentRevision);
        assessment.publish(publishedAt);
        writePublishAudit(adminAccountId, "SEE_ASSESSMENT", assessmentId);
        createPublicationEvents(
                marks.stream().map(SeeMark::getStudent).toList(),
                assessmentId,
                "SEE",
                assessment.getName(),
                "TPL-SEE-PUB",
                publishedAt);
        return toResponse(assessment);
    }

    @Transactional(readOnly = true)
    public List<PublishedMarkResponse> getPublishedMarks(Role role, Long accountId, Long studentId) {
        StudentProfile student = studentProfileRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
        if (role == Role.STUDENT) {
            if (!student.getAccount().getId().equals(accountId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
            }
        } else if (role == Role.PARENT) {
            ParentProfile parent = parentProfileRepository.findByAccount_Id(accountId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Parent profile not found"));
            boolean linked = parentStudentLinkRepository.findByParent_IdAndStudent_Id(parent.getId(), studentId)
                    .stream().anyMatch(ParentStudentLink::isActiveVerified);
            if (!linked) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No active verified link to this student");
            }
        } else if (role != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        List<PublishedMarkResponse> result = new ArrayList<>();
        for (CieMark mark : cieMarkRepository.findByStudent_Id(studentId)) {
            Integer publishedRevision = mark.getPublishedRevisionNumber();
            if (publishedRevision == null) {
                continue;
            }
            cieMarkHistoryRepository.findByMark_IdAndRevisionNumber(mark.getId(), publishedRevision)
                    .ifPresent(history -> result.add(new PublishedMarkResponse(
                            "CIE",
                            mark.getAssessment().getId(),
                            mark.getAssessment().getName(),
                            mark.getAssessment().getOffering().getSubject().getCode(),
                            mark.getAssessment().getOffering().getSubject().getName(),
                            history.getNewMarks().toPlainString(),
                            publishedRevision)));
        }
        for (SeeMark mark : seeMarkRepository.findByStudent_Id(studentId)) {
            Integer publishedRevision = mark.getPublishedRevisionNumber();
            if (publishedRevision == null) {
                continue;
            }
            seeMarkHistoryRepository.findByMark_IdAndRevisionNumber(mark.getId(), publishedRevision)
                    .ifPresent(history -> result.add(new PublishedMarkResponse(
                            "SEE",
                            mark.getAssessment().getId(),
                            mark.getAssessment().getName(),
                            mark.getAssessment().getOffering().getSubject().getCode(),
                            mark.getAssessment().getOffering().getSubject().getName(),
                            history.getNewValue(),
                            publishedRevision)));
        }
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public List<PublishedMarkResponse> getOwnPublishedMarks(Long accountId) {
        StudentProfile student = studentProfileRepository.findByAccount_Id(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Student profile not found"));
        return getPublishedMarks(Role.STUDENT, accountId, student.getId());
    }

    private void requireStaff(Role role) {
        if (role != Role.ADMIN && role != Role.PROFESSOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    private void ensureOfferingAccess(Role role, Long accountId, Long offeringId) {
        if (role == Role.PROFESSOR
                && !professorAssignmentRepository.existsByOffering_IdAndProfessor_Account_IdAndStatusIgnoreCase(
                        offeringId, accountId, "ACTIVE")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No active assignment to this offering");
        }
    }

    private StudentProfile requireEnrolledStudent(Long studentId, Long offeringId) {
        if (!enrollmentRepository.existsByStudent_IdAndOffering_IdAndStatusIgnoreCase(
                studentId, offeringId, "ACTIVE")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Student is not actively enrolled in offering");
        }
        return studentProfileRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
    }

    private UserAccount getAccount(Long accountId) {
        return userAccountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void writePublishAudit(Long accountId, String entityType, Long assessmentId) {
        auditLogRepository.save(new AuditLog(
                getAccount(accountId),
                Role.ADMIN.name(),
                entityType + "_PUBLISHED",
                entityType,
                assessmentId.toString(),
                null,
                Instant.now()));
    }

    private void createPublicationEvents(
            List<StudentProfile> students,
            Long assessmentId,
            String assessmentType,
            String assessmentName,
            String template,
            Instant publishedAt) {
        List<NotificationOutbox> events = new ArrayList<>();
        for (StudentProfile student : students) {
            List<ParentStudentLink> activeLinks = parentStudentLinkRepository
                    .findByStudent_Id(student.getId()).stream()
                    .filter(ParentStudentLink::isActiveVerified)
                    .toList();
            if (activeLinks.isEmpty()) {
                events.add(createPublicationEvent(
                        student, null, assessmentId, assessmentType, assessmentName, template, publishedAt));
                continue;
            }
            for (ParentStudentLink link : activeLinks) {
                events.add(createPublicationEvent(
                        student,
                        link.getParent(),
                        assessmentId,
                        assessmentType,
                        assessmentName,
                        template,
                        publishedAt));
            }
        }
        notificationOutboxRepository.saveAll(events);
    }

    private NotificationOutbox createPublicationEvent(
            StudentProfile student,
            ParentProfile parent,
            Long assessmentId,
            String assessmentType,
            String assessmentName,
            String template,
            Instant publishedAt) {
        String status = "SKIPPED_NO_OPTIN";
        if (parent != null && parent.hasVerifiedMobile() && parent.isWhatsappOptIn()) {
            NotificationPreference preference = notificationPreferenceRepository
                    .findByParent_IdAndCategory(parent.getId(), assessmentType)
                    .orElse(null);
            status = preference != null && !preference.isEnabled()
                    ? "SKIPPED_PREFERENCE"
                    : "PENDING";
        }
        String parentKey = parent == null ? "none" : parent.getId().toString();
        String idempotencyKey = assessmentType.toLowerCase(Locale.ROOT) + ":"
                + assessmentId + ":" + student.getId() + ":" + parentKey
                + ":" + assessmentName + ":" + publishedAt.toEpochMilli();
        String payload = "{\"assessmentId\":" + assessmentId
                + ",\"studentId\":" + student.getId()
                + ",\"assessmentType\":\"" + assessmentType + "\"}";
        return new NotificationOutbox(
                student,
                parent,
                assessmentType + "_PUBLISHED",
                assessmentId,
                template,
                payload,
                idempotencyKey,
                status,
                publishedAt);
    }

    private AssessmentResponse toResponse(CieAssessment assessment) {
        return new AssessmentResponse(
                assessment.getId(),
                assessment.getOffering().getId(),
                assessment.getName(),
                assessment.getMaximumMarks(),
                assessment.getPublicationStatus(),
                assessment.getPublishedAt());
    }

    private AssessmentResponse toResponse(SeeAssessment assessment) {
        return new AssessmentResponse(
                assessment.getId(),
                assessment.getOffering().getId(),
                assessment.getName(),
                null,
                assessment.getPublicationStatus(),
                assessment.getPublishedAt());
    }
}

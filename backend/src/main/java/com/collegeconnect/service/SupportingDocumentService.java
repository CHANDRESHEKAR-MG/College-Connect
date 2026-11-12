package com.collegeconnect.service;

import com.collegeconnect.dto.DocumentAuditEntryResponse;
import com.collegeconnect.dto.DocumentScanResultRequest;
import com.collegeconnect.dto.SupportingDocumentResponse;
import com.collegeconnect.entity.AbsenceCase;
import com.collegeconnect.entity.AuditLog;
import com.collegeconnect.entity.LeaveRequest;
import com.collegeconnect.entity.ParentProfile;
import com.collegeconnect.entity.ParentStudentLink;
import com.collegeconnect.entity.SupportingDocument;
import com.collegeconnect.entity.UserAccount;
import com.collegeconnect.repository.AbsenceCaseRepository;
import com.collegeconnect.repository.AuditLogRepository;
import com.collegeconnect.repository.ClassSessionRepository;
import com.collegeconnect.repository.LeaveRequestRepository;
import com.collegeconnect.repository.ParentProfileRepository;
import com.collegeconnect.repository.ParentStudentLinkRepository;
import com.collegeconnect.repository.SupportingDocumentRepository;
import com.collegeconnect.repository.UserAccountRepository;
import com.collegeconnect.security.Role;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SupportingDocumentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupportingDocumentService.class);

    private final AbsenceCaseRepository absenceCaseRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ClassSessionRepository classSessionRepository;
    private final ParentProfileRepository parentProfileRepository;
    private final ParentStudentLinkRepository parentStudentLinkRepository;
    private final SupportingDocumentRepository supportingDocumentRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuditLogRepository auditLogRepository;
    private final Path storageDirectory;
    private final long maximumFileSize;

    public SupportingDocumentService(
            AbsenceCaseRepository absenceCaseRepository,
            LeaveRequestRepository leaveRequestRepository,
            ClassSessionRepository classSessionRepository,
            ParentProfileRepository parentProfileRepository,
            ParentStudentLinkRepository parentStudentLinkRepository,
            SupportingDocumentRepository supportingDocumentRepository,
            UserAccountRepository userAccountRepository,
            AuditLogRepository auditLogRepository,
            @Value("${collegeconnect.documents.storage-directory:}") String storageDirectory,
            @Value("${collegeconnect.documents.max-size-bytes:5242880}") long maximumFileSize) {
        this.absenceCaseRepository = absenceCaseRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.classSessionRepository = classSessionRepository;
        this.parentProfileRepository = parentProfileRepository;
        this.parentStudentLinkRepository = parentStudentLinkRepository;
        this.supportingDocumentRepository = supportingDocumentRepository;
        this.userAccountRepository = userAccountRepository;
        this.auditLogRepository = auditLogRepository;
        this.storageDirectory = storageDirectory.isBlank() ? null : Path.of(storageDirectory).toAbsolutePath().normalize();
        this.maximumFileSize = maximumFileSize;
    }

    @Transactional
    public SupportingDocumentResponse uploadForAbsence(
            Long accountId, Long caseId, MultipartFile file) {
        AbsenceCase absenceCase = absenceCaseRepository.findByIdForUpdate(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Absence case not found"));
        ensureParentLinked(accountId, absenceCase.getAttendance().getStudent().getId());
        return store(accountId, Role.PARENT, absenceCase, null, file);
    }

    @Transactional
    public SupportingDocumentResponse uploadForLeave(
            Long accountId, Long requestId, MultipartFile file) {
        LeaveRequest leave = leaveRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave request not found"));
        if (!leave.getRequester().getId().equals(accountId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave request not found");
        }
        ensureParentLinked(accountId, leave.getStudent().getId());
        if (!"SUBMITTED".equalsIgnoreCase(leave.getStatus())
                && !"UNDER_REVIEW".equalsIgnoreCase(leave.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Documents cannot be added to this leave request");
        }
        return store(accountId, Role.PARENT, null, leave, file);
    }

    @Transactional(readOnly = true)
    public List<SupportingDocumentResponse> listForAbsence(
            Long accountId, Role role, Long caseId) {
        AbsenceCase absenceCase = getAbsenceCase(caseId);
        authorizeCaseAccess(accountId, role, absenceCase);
        return supportingDocumentRepository.findByAbsenceCase_IdOrderByUploadedAtAsc(caseId).stream()
                .filter(this::isActive)
                .filter(document -> canParentRead(accountId, role, document))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SupportingDocumentResponse> listForLeave(
            Long accountId, Role role, Long requestId) {
        LeaveRequest leave = getLeaveRequest(requestId);
        authorizeLeaveAccess(accountId, role, leave);
        return supportingDocumentRepository.findByLeaveRequest_IdOrderByUploadedAtAsc(requestId).stream()
                .filter(this::isActive)
                .filter(document -> canParentRead(accountId, role, document))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SupportingDocumentResponse replace(
            Long accountId, Long documentId, MultipartFile replacementFile) {
        SupportingDocument document = getDocumentForUpdate(documentId);
        requireParentOwnsDocument(accountId, document);
        ensureReplaceable(document);
        SupportingDocumentResponse replacement = document.getAbsenceCase() != null
                ? store(accountId, Role.PARENT, document.getAbsenceCase(), null, replacementFile)
                : store(accountId, Role.PARENT, null, document.getLeaveRequest(), replacementFile);
        document.markReplaced();
        recordAudit(
                accountId,
                Role.PARENT,
                "DOCUMENT_REPLACED",
                documentId,
                "Replaced by document " + replacement.documentId());
        deleteAfterCommit(resolveStoragePath(document.getPrivateStorageKey()));
        return replacement;
    }

    @Transactional
    public void delete(Long accountId, Long documentId) {
        SupportingDocument document = getDocumentForUpdate(documentId);
        requireParentOwnsDocument(accountId, document);
        if (!isActive(document)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Document is no longer active");
        }
        document.markDeleted();
        recordAudit(accountId, Role.PARENT, "DOCUMENT_DELETED", documentId, "Parent deleted document");
        deleteAfterCommit(resolveStoragePath(document.getPrivateStorageKey()));
    }

    @Transactional(readOnly = true)
    public List<DocumentAuditEntryResponse> getAuditHistory(
            Long adminAccountId, Role role, Long documentId) {
        if (role != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        getAccount(adminAccountId);
        getDocument(documentId);
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtAsc(
                        "SUPPORTING_DOCUMENT", documentId.toString())
                .stream()
                .map(audit -> new DocumentAuditEntryResponse(
                        audit.getId(),
                        audit.getActor() == null ? null : audit.getActor().getId(),
                        audit.getActorRole(),
                        audit.getAction(),
                        audit.getReason(),
                        audit.getCreatedAt()))
                .toList();
    }

    @Transactional
    public SupportingDocumentResponse recordScanResult(
            Long adminAccountId, Long documentId, DocumentScanResultRequest request) {
        SupportingDocument document = getDocumentForUpdate(documentId);
        if (!"PENDING_SCAN".equalsIgnoreCase(document.getScanStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Document scan result is already recorded");
        }
        String status = request.result().toUpperCase(Locale.ROOT);
        document.recordScanResult(status);
        recordAudit(
                adminAccountId,
                Role.ADMIN,
                "DOCUMENT_SCAN_" + status,
                documentId,
                request.reason().trim());
        return toResponse(document);
    }

    @Transactional
    public byte[] download(Long accountId, Role role, Long documentId) {
        SupportingDocument document = supportingDocumentRepository.findByIdForRead(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        authorizeDocumentAccess(accountId, role, document);
        if (!"CLEAN".equalsIgnoreCase(document.getScanStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Document is not available until scanning completes");
        }
        if (storageDirectory == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Private document storage is not configured");
        }
        Path path = resolveStoragePath(document.getPrivateStorageKey());
        try {
            byte[] content = Files.readAllBytes(path);
            recordAudit(accountId, role, "DOCUMENT_DOWNLOADED", documentId, "Authorized document download");
            return content;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read stored document", exception);
        }
    }

    @Transactional(readOnly = true)
    public SupportingDocumentResponse getMetadata(Long accountId, Role role, Long documentId) {
        SupportingDocument document = getDocument(documentId);
        authorizeDocumentAccess(accountId, role, document);
        return toResponse(document);
    }

    private SupportingDocumentResponse store(
            Long accountId, Role role, AbsenceCase absenceCase, LeaveRequest leave, MultipartFile file) {
        if (storageDirectory == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Private document storage is not configured");
        }
        if (file == null || file.isEmpty() || file.getSize() > maximumFileSize) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document is empty or exceeds the configured size limit");
        }
        String originalFileName = safeFileName(file.getOriginalFilename());
        String mediaType = validateMedia(file);
        validateFileExtension(originalFileName, mediaType);
        String storageKey = UUID.randomUUID().toString() + extension(mediaType);
        Path destination = resolveStoragePath(storageKey);

        try {
            Files.createDirectories(storageDirectory);
            Files.write(
                    destination,
                    file.getBytes(),
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store document", exception);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteFile(destination);
                }
            }
        });

        UserAccount uploader = getAccount(accountId);
        SupportingDocument document = supportingDocumentRepository.save(new SupportingDocument(
                absenceCase,
                leave,
                uploader,
                storageKey,
                originalFileName,
                mediaType,
                file.getSize(),
                "PENDING_SCAN",
                Instant.now()));
        recordAudit(accountId, role, "DOCUMENT_UPLOADED", document.getId(), "Supporting document uploaded");
        return toResponse(document);
    }

    private String validateMedia(MultipartFile file) {
        String contentType = file.getContentType();
        try {
            byte[] header = file.getBytes();
            if ("application/pdf".equalsIgnoreCase(contentType)
                    && header.length >= 5
                    && header[0] == '%'
                    && header[1] == 'P'
                    && header[2] == 'D'
                    && header[3] == 'F'
                    && header[4] == '-') {
                return "application/pdf";
            }
            if ("image/jpeg".equalsIgnoreCase(contentType)
                    && header.length >= 3
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF) {
                return "image/jpeg";
            }
            if ("image/png".equalsIgnoreCase(contentType)
                    && header.length >= 8
                    && (header[0] & 0xFF) == 0x89
                    && header[1] == 'P'
                    && header[2] == 'N'
                    && header[3] == 'G') {
                return "image/png";
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read uploaded document", exception);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF, JPEG, and PNG documents are accepted");
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document filename is required");
        }
        String safeName = fileName.replace('\\', '/');
        safeName = safeName.substring(safeName.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        if (safeName.isBlank() || safeName.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document filename");
        }
        return safeName;
    }

    private void validateFileExtension(String fileName, String mediaType) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        boolean matches = switch (mediaType) {
            case "application/pdf" -> lowerName.endsWith(".pdf");
            case "image/jpeg" -> lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg");
            case "image/png" -> lowerName.endsWith(".png");
            default -> false;
        };
        if (!matches) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filename extension does not match document content");
        }
    }

    private Path resolveStoragePath(String storageKey) {
        if (storageDirectory == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Private document storage is not configured");
        }
        Path resolved = storageDirectory.resolve(storageKey).normalize();
        if (!resolved.getParent().equals(storageDirectory)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid document storage key");
        }
        return resolved;
    }

    private String extension(String mediaType) {
        return switch (mediaType) {
            case "application/pdf" -> ".pdf";
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> throw new IllegalArgumentException("Unsupported media type");
        };
    }

    private void ensureParentLinked(Long accountId, Long studentId) {
        ParentProfile parent = parentProfileRepository.findByAccount_Id(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Parent profile not found"));
        boolean linked = parentStudentLinkRepository.findByParent_IdAndStudent_Id(parent.getId(), studentId).stream()
                .anyMatch(ParentStudentLink::isActiveVerified);
        if (!linked) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No active verified link to this student");
        }
    }

    private void authorizeDocumentAccess(Long accountId, Role role, SupportingDocument document) {
        if (role == Role.ADMIN) {
            return;
        }
        if (role == Role.PARENT) {
            ensureParentLinked(accountId, studentId(document));
            if (!canParentRead(accountId, role, document)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
            }
            return;
        }
        if (role == Role.PROFESSOR) {
            if (document.getAbsenceCase() != null) {
                Long sessionId = document.getAbsenceCase().getAttendance().getSession().getId();
                if (classSessionRepository.isAssignedProfessorForSession(sessionId, accountId)) {
                    return;
                }
            } else {
                LeaveRequest leave = document.getLeaveRequest();
                if (classSessionRepository.hasAssignedSessionForSectionAndDateRange(
                        accountId,
                        leave.getStudent().getSection().getId(),
                        leave.getStartDate(),
                        leave.getEndDate())) {
                    return;
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
    }

    private void authorizeCaseAccess(Long accountId, Role role, AbsenceCase absenceCase) {
        if (role == Role.ADMIN
                || role == Role.PROFESSOR && classSessionRepository.isAssignedProfessorForSession(
                        absenceCase.getAttendance().getSession().getId(), accountId)) {
            return;
        }
        if (role == Role.PARENT) {
            ensureParentLinked(accountId, absenceCase.getAttendance().getStudent().getId());
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
    }

    private void authorizeLeaveAccess(Long accountId, Role role, LeaveRequest leave) {
        if (role == Role.ADMIN
                || role == Role.PROFESSOR && classSessionRepository.hasAssignedSessionForSectionAndDateRange(
                        accountId,
                        leave.getStudent().getSection().getId(),
                        leave.getStartDate(),
                        leave.getEndDate())) {
            return;
        }
        if (role == Role.PARENT) {
            ensureParentLinked(accountId, leave.getStudent().getId());
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
    }

    private void requireParentOwnsDocument(Long accountId, SupportingDocument document) {
        if (!document.getUploadedBy().getId().equals(accountId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the uploader may replace or delete a document");
        }
        ensureParentLinked(accountId, studentId(document));
    }

    private void ensureReplaceable(SupportingDocument document) {
        if (!isActive(document)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Document is no longer active");
        }
        if (document.getAbsenceCase() != null) {
            String status = document.getAbsenceCase().getStatus();
            if ("ACCEPTED".equalsIgnoreCase(status)
                    || "REJECTED".equalsIgnoreCase(status)
                    || "CLOSED_CORRECTED".equalsIgnoreCase(status)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Documents cannot be replaced after review");
            }
        } else {
            String status = document.getLeaveRequest().getStatus();
            if (!"SUBMITTED".equalsIgnoreCase(status) && !"UNDER_REVIEW".equalsIgnoreCase(status)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Documents cannot be replaced after review");
            }
        }
    }

    private boolean isActive(SupportingDocument document) {
        return !"REPLACED".equalsIgnoreCase(document.getScanStatus())
                && !"DELETED".equalsIgnoreCase(document.getScanStatus());
    }

    private boolean canParentRead(Long accountId, Role role, SupportingDocument document) {
        return role != Role.PARENT
                || document.getUploadedBy().getId().equals(accountId)
                || "ADMIN".equalsIgnoreCase(document.getUploadedBy().getRole());
    }

    private Long studentId(SupportingDocument document) {
        return document.getAbsenceCase() != null
                ? document.getAbsenceCase().getAttendance().getStudent().getId()
                : document.getLeaveRequest().getStudent().getId();
    }

    private AbsenceCase getAbsenceCase(Long caseId) {
        return absenceCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Absence case not found"));
    }

    private LeaveRequest getLeaveRequest(Long requestId) {
        return leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Leave request not found"));
    }

    private SupportingDocument getDocument(Long documentId) {
        return supportingDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    private SupportingDocument getDocumentForUpdate(Long documentId) {
        return supportingDocumentRepository.findByIdForUpdate(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    private void recordAudit(
            Long accountId, Role role, String action, Long documentId, String reason) {
        auditLogRepository.save(new AuditLog(
                getAccount(accountId),
                role.name(),
                action,
                "SUPPORTING_DOCUMENT",
                documentId.toString(),
                reason,
                Instant.now()));
    }

    private void deleteAfterCommit(Path path) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteFile(path);
            }
        });
    }

    private void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            LOGGER.error("Unable to remove private document file {}", path, exception);
        }
    }

    private UserAccount getAccount(Long accountId) {
        return userAccountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private SupportingDocumentResponse toResponse(SupportingDocument document) {
        return new SupportingDocumentResponse(
                document.getId(),
                document.getOriginalFileName(),
                document.getMediaType(),
                document.getFileSizeBytes(),
                document.getScanStatus(),
                document.getUploadedAt());
    }
}

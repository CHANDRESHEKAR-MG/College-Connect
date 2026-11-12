package com.collegeconnect.controller;

import com.collegeconnect.dto.DocumentScanResultRequest;
import com.collegeconnect.dto.DocumentAuditEntryResponse;
import com.collegeconnect.dto.SupportingDocumentResponse;
import com.collegeconnect.security.Role;
import com.collegeconnect.service.SupportingDocumentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class SupportingDocumentController {

    private final SupportingDocumentService supportingDocumentService;

    public SupportingDocumentController(SupportingDocumentService supportingDocumentService) {
        this.supportingDocumentService = supportingDocumentService;
    }

    @PostMapping(
            path = "/api/parent/absence-cases/{caseId}/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SupportingDocumentResponse> uploadForAbsence(
            @PathVariable("caseId") Long caseId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        return ResponseEntity.ok(
                supportingDocumentService.uploadForAbsence(currentUserId(authentication), caseId, file));
    }

    @PostMapping(
            path = "/api/parent/leave-requests/{requestId}/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SupportingDocumentResponse> uploadForLeave(
            @PathVariable("requestId") Long requestId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        return ResponseEntity.ok(
                supportingDocumentService.uploadForLeave(currentUserId(authentication), requestId, file));
    }

    @GetMapping("/api/parent/absence-cases/{caseId}/documents")
    public ResponseEntity<List<SupportingDocumentResponse>> listParentAbsenceDocuments(
            @PathVariable("caseId") Long caseId, Authentication authentication) {
        return ResponseEntity.ok(supportingDocumentService.listForAbsence(
                currentUserId(authentication), Role.PARENT, caseId));
    }

    @GetMapping("/api/parent/leave-requests/{requestId}/documents")
    public ResponseEntity<List<SupportingDocumentResponse>> listParentLeaveDocuments(
            @PathVariable("requestId") Long requestId, Authentication authentication) {
        return ResponseEntity.ok(supportingDocumentService.listForLeave(
                currentUserId(authentication), Role.PARENT, requestId));
    }

    @GetMapping("/api/professor/absence-cases/{caseId}/documents")
    public ResponseEntity<List<SupportingDocumentResponse>> listProfessorAbsenceDocuments(
            @PathVariable("caseId") Long caseId, Authentication authentication) {
        return ResponseEntity.ok(supportingDocumentService.listForAbsence(
                currentUserId(authentication), Role.PROFESSOR, caseId));
    }

    @GetMapping("/api/professor/leave-requests/{requestId}/documents")
    public ResponseEntity<List<SupportingDocumentResponse>> listProfessorLeaveDocuments(
            @PathVariable("requestId") Long requestId, Authentication authentication) {
        return ResponseEntity.ok(supportingDocumentService.listForLeave(
                currentUserId(authentication), Role.PROFESSOR, requestId));
    }

    @GetMapping("/api/admin/absence-cases/{caseId}/documents")
    public ResponseEntity<List<SupportingDocumentResponse>> listAdminAbsenceDocuments(
            @PathVariable("caseId") Long caseId, Authentication authentication) {
        return ResponseEntity.ok(supportingDocumentService.listForAbsence(
                currentUserId(authentication), Role.ADMIN, caseId));
    }

    @GetMapping("/api/admin/leave-requests/{requestId}/documents")
    public ResponseEntity<List<SupportingDocumentResponse>> listAdminLeaveDocuments(
            @PathVariable("requestId") Long requestId, Authentication authentication) {
        return ResponseEntity.ok(supportingDocumentService.listForLeave(
                currentUserId(authentication), Role.ADMIN, requestId));
    }

    @PostMapping(
            path = "/api/parent/supporting-documents/{documentId}/replacement",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SupportingDocumentResponse> replaceForParent(
            @PathVariable("documentId") Long documentId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        return ResponseEntity.ok(
                supportingDocumentService.replace(currentUserId(authentication), documentId, file));
    }

    @DeleteMapping("/api/parent/supporting-documents/{documentId}")
    public ResponseEntity<Void> deleteForParent(
            @PathVariable("documentId") Long documentId, Authentication authentication) {
        supportingDocumentService.delete(currentUserId(authentication), documentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/supporting-documents/{documentId}/scan-result")
    public ResponseEntity<SupportingDocumentResponse> recordScanResult(
            @PathVariable("documentId") Long documentId,
            @Valid @RequestBody DocumentScanResultRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(supportingDocumentService.recordScanResult(
                currentUserId(authentication), documentId, request));
    }

    @GetMapping("/api/parent/supporting-documents/{documentId}")
    public ResponseEntity<byte[]> downloadForParent(
            @PathVariable("documentId") Long documentId, Authentication authentication) {
        return download(currentUserId(authentication), Role.PARENT, documentId);
    }

    @GetMapping("/api/admin/supporting-documents/{documentId}")
    public ResponseEntity<byte[]> downloadForAdmin(
            @PathVariable("documentId") Long documentId, Authentication authentication) {
        return download(currentUserId(authentication), Role.ADMIN, documentId);
    }

    @GetMapping("/api/professor/supporting-documents/{documentId}")
    public ResponseEntity<byte[]> downloadForProfessor(
            @PathVariable("documentId") Long documentId, Authentication authentication) {
        return download(currentUserId(authentication), Role.PROFESSOR, documentId);
    }

    @GetMapping("/api/admin/supporting-documents/{documentId}/audit")
    public ResponseEntity<List<DocumentAuditEntryResponse>> getAuditHistory(
            @PathVariable("documentId") Long documentId, Authentication authentication) {
        return ResponseEntity.ok(supportingDocumentService.getAuditHistory(
                currentUserId(authentication), Role.ADMIN, documentId));
    }

    private ResponseEntity<byte[]> download(Long accountId, Role role, Long documentId) {
        SupportingDocumentResponse metadata =
                supportingDocumentService.getMetadata(accountId, role, documentId);
        byte[] content = supportingDocumentService.download(accountId, role, documentId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(metadata.mediaType()));
        headers.setContentDisposition(ContentDisposition.attachment().filename(metadata.fileName()).build());
        headers.setContentLength(content.length);
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl("private, no-store");
        return ResponseEntity.ok().headers(headers).body(content);
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}

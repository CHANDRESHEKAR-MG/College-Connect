package com.collegeconnect.controller;

import com.collegeconnect.dto.AbsenceCaseResponse;
import com.collegeconnect.dto.AbsenceExplanationRequest;
import com.collegeconnect.dto.AbsenceReviewRequest;
import com.collegeconnect.dto.LeaveRequestCreateRequest;
import com.collegeconnect.dto.LeaveRequestResponse;
import com.collegeconnect.dto.LeaveReviewRequest;
import com.collegeconnect.security.Role;
import com.collegeconnect.service.ParentWorkflowService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ParentWorkflowController {

    private final ParentWorkflowService parentWorkflowService;

    public ParentWorkflowController(ParentWorkflowService parentWorkflowService) {
        this.parentWorkflowService = parentWorkflowService;
    }

    @GetMapping("/api/parent/students/{studentId}/absence-cases")
    public ResponseEntity<List<AbsenceCaseResponse>> listAbsences(
            @PathVariable("studentId") Long studentId,
            Authentication authentication) {
        return ResponseEntity.ok(parentWorkflowService.listParentAbsences(currentUserId(authentication), studentId));
    }

    @GetMapping("/api/student/absence-cases")
    public ResponseEntity<List<AbsenceCaseResponse>> listStudentAbsences(Authentication authentication) {
        return ResponseEntity.ok(parentWorkflowService.listStudentAbsences(currentUserId(authentication)));
    }

    @GetMapping("/api/professor/absence-cases")
    public ResponseEntity<List<AbsenceCaseResponse>> listProfessorAbsences(Authentication authentication) {
        return ResponseEntity.ok(parentWorkflowService.listProfessorAbsences(
                currentUserId(authentication), Role.PROFESSOR));
    }

    @GetMapping("/api/admin/absence-cases")
    public ResponseEntity<List<AbsenceCaseResponse>> listAdminAbsences(Authentication authentication) {
        return ResponseEntity.ok(parentWorkflowService.listAdminAbsences(
                currentUserId(authentication), Role.ADMIN));
    }

    @PostMapping("/api/parent/absence-cases/{caseId}/explanation")
    public ResponseEntity<AbsenceCaseResponse> explainAbsence(
            @PathVariable("caseId") Long caseId,
            @Valid @RequestBody AbsenceExplanationRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(parentWorkflowService.explainAbsence(currentUserId(authentication), caseId, request));
    }

    @PostMapping("/api/admin/absence-cases/{caseId}/review")
    public ResponseEntity<AbsenceCaseResponse> reviewAbsence(
            @PathVariable("caseId") Long caseId,
            @Valid @RequestBody AbsenceReviewRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(parentWorkflowService.reviewAbsence(currentUserId(authentication), caseId, request));
    }

    @PostMapping("/api/parent/leave-requests")
    public ResponseEntity<LeaveRequestResponse> createLeave(
            @Valid @RequestBody LeaveRequestCreateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(parentWorkflowService.createLeave(currentUserId(authentication), request));
    }

    @GetMapping("/api/parent/leave-requests")
    public ResponseEntity<List<LeaveRequestResponse>> listLeave(Authentication authentication) {
        return ResponseEntity.ok(parentWorkflowService.listParentLeave(currentUserId(authentication)));
    }

    @GetMapping("/api/professor/leave-requests")
    public ResponseEntity<List<LeaveRequestResponse>> listProfessorLeave(Authentication authentication) {
        return ResponseEntity.ok(parentWorkflowService.listProfessorLeave(
                currentUserId(authentication), Role.PROFESSOR));
    }

    @GetMapping("/api/admin/leave-requests")
    public ResponseEntity<List<LeaveRequestResponse>> listAdminLeave(Authentication authentication) {
        return ResponseEntity.ok(parentWorkflowService.listAdminLeave(
                currentUserId(authentication), Role.ADMIN));
    }

    @PostMapping("/api/parent/leave-requests/{requestId}/cancel")
    public ResponseEntity<LeaveRequestResponse> cancelLeave(
            @PathVariable("requestId") Long requestId,
            Authentication authentication) {
        return ResponseEntity.ok(parentWorkflowService.cancelLeave(currentUserId(authentication), requestId));
    }

    @PostMapping("/api/admin/leave-requests/{requestId}/review")
    public ResponseEntity<LeaveRequestResponse> reviewLeave(
            @PathVariable("requestId") Long requestId,
            @Valid @RequestBody LeaveReviewRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(parentWorkflowService.reviewLeave(currentUserId(authentication), requestId, request));
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}

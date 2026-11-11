package com.collegeconnect.controller;

import com.collegeconnect.dto.AssessmentCreateRequest;
import com.collegeconnect.dto.AssessmentResponse;
import com.collegeconnect.dto.MarkEntryRequest;
import com.collegeconnect.dto.PublishedMarkResponse;
import com.collegeconnect.dto.SeeAssessmentCreateRequest;
import com.collegeconnect.security.Role;
import com.collegeconnect.service.MarkManagementService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MarkManagementController {

    private final MarkManagementService markManagementService;

    public MarkManagementController(MarkManagementService markManagementService) {
        this.markManagementService = markManagementService;
    }

    @PostMapping("/api/professor/cie/assessments")
    public ResponseEntity<AssessmentResponse> createCie(
            @Valid @RequestBody AssessmentCreateRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                markManagementService.createCie(Role.PROFESSOR, currentUserId(authentication), request));
    }

    @PostMapping("/api/admin/cie/assessments")
    public ResponseEntity<AssessmentResponse> createCieAsAdmin(
            @Valid @RequestBody AssessmentCreateRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                markManagementService.createCie(Role.ADMIN, currentUserId(authentication), request));
    }

    @PostMapping("/api/professor/cie/assessments/{assessmentId}/marks")
    public ResponseEntity<Void> enterCieMark(
            @PathVariable("assessmentId") Long assessmentId,
            @Valid @RequestBody MarkEntryRequest request,
            Authentication authentication) {
        markManagementService.enterCieMark(Role.PROFESSOR, currentUserId(authentication), assessmentId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/cie/assessments/{assessmentId}/marks")
    public ResponseEntity<Void> enterCieMarkAsAdmin(
            @PathVariable("assessmentId") Long assessmentId,
            @Valid @RequestBody MarkEntryRequest request,
            Authentication authentication) {
        markManagementService.enterCieMark(Role.ADMIN, currentUserId(authentication), assessmentId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/cie/assessments/{assessmentId}/publish")
    public ResponseEntity<AssessmentResponse> publishCie(
            @PathVariable("assessmentId") Long assessmentId, Authentication authentication) {
        return ResponseEntity.ok(markManagementService.publishCie(assessmentId, currentUserId(authentication)));
    }

    @PostMapping("/api/professor/see/assessments")
    public ResponseEntity<AssessmentResponse> createSee(
            @Valid @RequestBody SeeAssessmentCreateRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                markManagementService.createSee(Role.PROFESSOR, currentUserId(authentication), request));
    }

    @PostMapping("/api/admin/see/assessments")
    public ResponseEntity<AssessmentResponse> createSeeAsAdmin(
            @Valid @RequestBody SeeAssessmentCreateRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                markManagementService.createSee(Role.ADMIN, currentUserId(authentication), request));
    }

    @PostMapping("/api/professor/see/assessments/{assessmentId}/marks")
    public ResponseEntity<Void> enterSeeMark(
            @PathVariable("assessmentId") Long assessmentId,
            @Valid @RequestBody MarkEntryRequest request,
            Authentication authentication) {
        markManagementService.enterSeeMark(Role.PROFESSOR, currentUserId(authentication), assessmentId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/see/assessments/{assessmentId}/marks")
    public ResponseEntity<Void> enterSeeMarkAsAdmin(
            @PathVariable("assessmentId") Long assessmentId,
            @Valid @RequestBody MarkEntryRequest request,
            Authentication authentication) {
        markManagementService.enterSeeMark(Role.ADMIN, currentUserId(authentication), assessmentId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/see/assessments/{assessmentId}/publish")
    public ResponseEntity<AssessmentResponse> publishSee(
            @PathVariable("assessmentId") Long assessmentId, Authentication authentication) {
        return ResponseEntity.ok(markManagementService.publishSee(assessmentId, currentUserId(authentication)));
    }

    @GetMapping("/api/student/marks")
    public ResponseEntity<List<PublishedMarkResponse>> ownMarks(Authentication authentication) {
        return ResponseEntity.ok(markManagementService.getOwnPublishedMarks(currentUserId(authentication)));
    }

    @GetMapping("/api/parent/students/{studentId}/marks")
    public ResponseEntity<List<PublishedMarkResponse>> parentMarks(
            @PathVariable("studentId") Long studentId,
            Authentication authentication) {
        return ResponseEntity.ok(markManagementService.getPublishedMarks(
                Role.PARENT, currentUserId(authentication), studentId));
    }

    @GetMapping("/api/admin/students/{studentId}/marks")
    public ResponseEntity<List<PublishedMarkResponse>> adminMarks(
            @PathVariable("studentId") Long studentId,
            Authentication authentication) {
        return ResponseEntity.ok(markManagementService.getPublishedMarks(
                Role.ADMIN, currentUserId(authentication), studentId));
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}

package com.collegeconnect.controller;

import com.collegeconnect.dto.AttendanceCorrectionHistoryResponse;
import com.collegeconnect.dto.AttendanceCorrectionRequest;
import com.collegeconnect.dto.AttendanceCorrectionResponse;
import com.collegeconnect.dto.AttendanceRosterResponse;
import com.collegeconnect.dto.AttendanceSubmissionRequest;
import com.collegeconnect.dto.AttendanceSubmissionResponse;
import com.collegeconnect.security.Role;
import com.collegeconnect.service.AttendanceCorrectionService;
import com.collegeconnect.service.AttendanceRosterService;
import com.collegeconnect.service.AttendanceSubmissionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AttendanceRosterController {

    private final AttendanceRosterService attendanceRosterService;
    private final AttendanceSubmissionService attendanceSubmissionService;
    private final AttendanceCorrectionService attendanceCorrectionService;

    public AttendanceRosterController(
            AttendanceRosterService attendanceRosterService,
            AttendanceSubmissionService attendanceSubmissionService,
            AttendanceCorrectionService attendanceCorrectionService) {
        this.attendanceRosterService = attendanceRosterService;
        this.attendanceSubmissionService = attendanceSubmissionService;
        this.attendanceCorrectionService = attendanceCorrectionService;
    }

    @GetMapping("/api/professor/attendance/sessions/{sessionId}/roster")
    public ResponseEntity<AttendanceRosterResponse> professorRoster(
            @PathVariable("sessionId") Long sessionId,
            Authentication authentication) {
        return ResponseEntity.ok(attendanceRosterService.getRoster(
                sessionId, currentUserId(authentication), Role.PROFESSOR));
    }

    @GetMapping("/api/admin/attendance/sessions/{sessionId}/roster")
    public ResponseEntity<AttendanceRosterResponse> adminRoster(
            @PathVariable("sessionId") Long sessionId,
            Authentication authentication) {
        return ResponseEntity.ok(attendanceRosterService.getRoster(
                sessionId, currentUserId(authentication), Role.ADMIN));
    }

    @PostMapping("/api/professor/attendance/sessions/{sessionId}/submit")
    public ResponseEntity<AttendanceSubmissionResponse> submitAttendance(
            @PathVariable("sessionId") Long sessionId,
            @Valid @RequestBody AttendanceSubmissionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(attendanceSubmissionService.submit(
                sessionId, currentUserId(authentication), Role.PROFESSOR, request));
    }

    @PostMapping("/api/admin/attendance/sessions/{sessionId}/submit")
    public ResponseEntity<AttendanceSubmissionResponse> submitAttendanceAsAdmin(
            @PathVariable("sessionId") Long sessionId,
            @Valid @RequestBody AttendanceSubmissionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(attendanceSubmissionService.submit(
                sessionId, currentUserId(authentication), Role.ADMIN, request));
    }

    @PostMapping("/api/professor/attendance/records/{attendanceId}/corrections")
    public ResponseEntity<AttendanceCorrectionResponse> correctAsProfessor(
            @PathVariable("attendanceId") Long attendanceId,
            @Valid @RequestBody AttendanceCorrectionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(attendanceCorrectionService.correct(
                attendanceId, currentUserId(authentication), Role.PROFESSOR, request));
    }

    @PostMapping("/api/admin/attendance/records/{attendanceId}/corrections")
    public ResponseEntity<AttendanceCorrectionResponse> correctAsAdmin(
            @PathVariable("attendanceId") Long attendanceId,
            @Valid @RequestBody AttendanceCorrectionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(attendanceCorrectionService.correct(
                attendanceId, currentUserId(authentication), Role.ADMIN, request));
    }

    @GetMapping("/api/professor/attendance/records/{attendanceId}/corrections")
    public ResponseEntity<AttendanceCorrectionHistoryResponse> professorCorrectionHistory(
            @PathVariable("attendanceId") Long attendanceId,
            Authentication authentication) {
        return ResponseEntity.ok(attendanceCorrectionService.getHistory(
                attendanceId, currentUserId(authentication), Role.PROFESSOR));
    }

    @GetMapping("/api/admin/attendance/records/{attendanceId}/corrections")
    public ResponseEntity<AttendanceCorrectionHistoryResponse> adminCorrectionHistory(
            @PathVariable("attendanceId") Long attendanceId,
            Authentication authentication) {
        return ResponseEntity.ok(attendanceCorrectionService.getHistory(
                attendanceId, currentUserId(authentication), Role.ADMIN));
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}

package com.collegeconnect.controller;

import com.collegeconnect.dto.AttendanceSummaryResponse;
import com.collegeconnect.security.Role;
import com.collegeconnect.service.AttendanceReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AttendanceReportController {

    private final AttendanceReportService attendanceReportService;

    public AttendanceReportController(AttendanceReportService attendanceReportService) {
        this.attendanceReportService = attendanceReportService;
    }

    @GetMapping("/api/student/attendance/summary")
    public ResponseEntity<AttendanceSummaryResponse> ownSummary(Authentication authentication) {
        return ResponseEntity.ok(attendanceReportService.getOwnSummary(currentUserId(authentication)));
    }

    @GetMapping("/api/parent/students/{studentId}/attendance/summary")
    public ResponseEntity<AttendanceSummaryResponse> parentSummary(
            @PathVariable("studentId") Long studentId, Authentication authentication) {
        return ResponseEntity.ok(attendanceReportService.getSummary(
                Role.PARENT, currentUserId(authentication), studentId));
    }

    @GetMapping("/api/admin/students/{studentId}/attendance/summary")
    public ResponseEntity<AttendanceSummaryResponse> adminSummary(
            @PathVariable("studentId") Long studentId, Authentication authentication) {
        return ResponseEntity.ok(attendanceReportService.getSummary(
                Role.ADMIN, currentUserId(authentication), studentId));
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}

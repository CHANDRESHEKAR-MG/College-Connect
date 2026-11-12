package com.collegeconnect.controller;

import com.collegeconnect.dto.AttendanceReportRow;
import com.collegeconnect.dto.AttendanceGroupSummary;
import com.collegeconnect.security.Role;
import com.collegeconnect.service.AttendanceRangeReportService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AttendanceRangeReportController {

    private static final MediaType CSV_MEDIA_TYPE = MediaType.parseMediaType("text/csv;charset=UTF-8");

    private final AttendanceRangeReportService reportService;

    public AttendanceRangeReportController(AttendanceRangeReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/api/admin/reports/attendance")
    public ResponseEntity<List<AttendanceReportRow>> adminReport(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long offeringId,
            Authentication authentication) {
        return ResponseEntity.ok(rows(
                Role.ADMIN, authentication, studentId, sectionId, offeringId, startDate, endDate));
    }

    @GetMapping("/api/professor/reports/attendance")
    public ResponseEntity<List<AttendanceReportRow>> professorReport(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long offeringId,
            Authentication authentication) {
        return ResponseEntity.ok(rows(
                Role.PROFESSOR, authentication, studentId, sectionId, offeringId, startDate, endDate));
    }

    @GetMapping("/api/admin/reports/attendance/summary")
    public ResponseEntity<List<AttendanceGroupSummary>> adminSummary(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            @RequestParam String groupType,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long offeringId,
            Authentication authentication) {
        return ResponseEntity.ok(reportService.summary(
                Role.ADMIN,
                Long.valueOf(authentication.getName()),
                sectionId,
                offeringId,
                startDate,
                endDate,
                groupType));
    }

    @GetMapping("/api/professor/reports/attendance/summary")
    public ResponseEntity<List<AttendanceGroupSummary>> professorSummary(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            @RequestParam String groupType,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long offeringId,
            Authentication authentication) {
        return ResponseEntity.ok(reportService.summary(
                Role.PROFESSOR,
                Long.valueOf(authentication.getName()),
                sectionId,
                offeringId,
                startDate,
                endDate,
                groupType));
    }

    @GetMapping("/api/admin/reports/attendance/export.csv")
    public ResponseEntity<String> adminCsv(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long offeringId,
            Authentication authentication) {
        return csv(rows(Role.ADMIN, authentication, studentId, sectionId, offeringId, startDate, endDate));
    }

    @GetMapping("/api/professor/reports/attendance/export.csv")
    public ResponseEntity<String> professorCsv(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long offeringId,
            Authentication authentication) {
        return csv(rows(
                Role.PROFESSOR, authentication, studentId, sectionId, offeringId, startDate, endDate));
    }

    @GetMapping("/api/student/reports/attendance")
    public ResponseEntity<List<AttendanceReportRow>> studentReport(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            Authentication authentication) {
        return ResponseEntity.ok(rows(
                Role.STUDENT, authentication, null, null, null, startDate, endDate));
    }

    @GetMapping("/api/parent/students/{studentId}/reports/attendance")
    public ResponseEntity<List<AttendanceReportRow>> parentReport(
            @PathVariable Long studentId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            Authentication authentication) {
        return ResponseEntity.ok(rows(
                Role.PARENT, authentication, studentId, null, null, startDate, endDate));
    }

    private List<AttendanceReportRow> rows(
            Role role,
            Authentication authentication,
            Long studentId,
            Long sectionId,
            Long offeringId,
            LocalDate startDate,
            LocalDate endDate) {
        return reportService.report(
                role,
                Long.valueOf(authentication.getName()),
                studentId,
                sectionId,
                offeringId,
                startDate,
                endDate);
    }

    private ResponseEntity<String> csv(List<AttendanceReportRow> rows) {
        return ResponseEntity.ok()
                .contentType(CSV_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=attendance-report.csv")
                .body(reportService.toCsv(rows));
    }
}

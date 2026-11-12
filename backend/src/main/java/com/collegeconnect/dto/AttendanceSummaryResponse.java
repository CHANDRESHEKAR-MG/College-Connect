package com.collegeconnect.dto;

import java.math.BigDecimal;

public record AttendanceSummaryResponse(
        Long studentId,
        String studentName,
        int submittedSessions,
        int presentSessions,
        int absentSessions,
        BigDecimal attendancePercentage) {
}

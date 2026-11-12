package com.collegeconnect.dto;

import java.time.Instant;
import java.time.LocalDate;

public record LeaveRequestResponse(
        Long requestId,
        Long studentId,
        String studentName,
        Long requesterAccountId,
        LocalDate startDate,
        LocalDate endDate,
        String reasonCategory,
        String reason,
        String status,
        String reviewComment,
        Instant submittedAt,
        Instant reviewedAt) {
}

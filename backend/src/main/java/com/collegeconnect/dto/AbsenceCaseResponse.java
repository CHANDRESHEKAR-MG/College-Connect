package com.collegeconnect.dto;

import java.time.Instant;

public record AbsenceCaseResponse(
        Long caseId,
        Long attendanceId,
        Long studentId,
        Long sessionId,
        String status,
        String explanation,
        String reviewComments,
        Instant createdAt,
        Instant updatedAt) {
}

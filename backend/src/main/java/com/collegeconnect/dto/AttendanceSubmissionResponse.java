package com.collegeconnect.dto;

import java.time.Instant;

public record AttendanceSubmissionResponse(
        Long sessionId,
        String status,
        Instant submittedAt,
        int recordCount) {
}

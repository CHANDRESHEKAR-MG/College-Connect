package com.collegeconnect.dto;

import java.time.Instant;

public record AttendanceCorrectionResponse(
        Long correctionId,
        Long attendanceId,
        String previousStatus,
        String newStatus,
        Long actorAccountId,
        String actorLoginIdentifier,
        String actorRole,
        String reason,
        String comment,
        int correctionSequence,
        Instant correctedAt) {
}

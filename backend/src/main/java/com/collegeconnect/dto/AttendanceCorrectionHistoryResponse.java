package com.collegeconnect.dto;

import java.time.Instant;
import java.util.List;

public record AttendanceCorrectionHistoryResponse(
        Long attendanceId,
        String currentStatus,
        String originalSubmitter,
        Instant submittedAt,
        List<AttendanceCorrectionResponse> corrections) {
}

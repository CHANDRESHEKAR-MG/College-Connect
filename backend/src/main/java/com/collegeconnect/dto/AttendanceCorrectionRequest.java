package com.collegeconnect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AttendanceCorrectionRequest(
        @NotNull Long expectedVersion,
        @NotBlank @Pattern(regexp = "PRESENT|ABSENT|AUTHORIZED_LEAVE", flags = Pattern.Flag.CASE_INSENSITIVE)
                String newStatus,
        @NotBlank @Size(max = 1000) String reason,
        @Size(max = 1000) String comment) {
}

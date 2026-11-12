package com.collegeconnect.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AttendanceMarkRequest(
        @NotNull Long studentId,
        @NotNull
        @Pattern(regexp = "PRESENT|ABSENT|AUTHORIZED_LEAVE", flags = Pattern.Flag.CASE_INSENSITIVE)
        String status) {
}

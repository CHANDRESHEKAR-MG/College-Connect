package com.collegeconnect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GlobalNotificationPreferenceRequest(
        @NotBlank
        @Pattern(regexp = "ATTENDANCE_ABSENT|ATTENDANCE_PRESENT|ATTENDANCE_SUMMARY|LEAVE|LEAVE_SUBMITTED|ABSENCE|ABSENCE_REMINDER|CIE|SEE|ALL_MESSAGES")
        String category,
        @NotNull Boolean enabled,
        @NotBlank @Size(max = 1000) String reason) {}

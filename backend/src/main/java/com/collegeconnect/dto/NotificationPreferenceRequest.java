package com.collegeconnect.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record NotificationPreferenceRequest(
        @NotNull
        @Pattern(regexp = "ATTENDANCE_ABSENT|ATTENDANCE_PRESENT|ATTENDANCE_SUMMARY|LEAVE|LEAVE_SUBMITTED|ABSENCE|ABSENCE_REMINDER|CIE|SEE|ALL_MESSAGES")
        String category,
        @NotNull Boolean enabled) {
}

package com.collegeconnect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NotificationResendRequest(@NotBlank @Size(max = 1000) String reason) {
}

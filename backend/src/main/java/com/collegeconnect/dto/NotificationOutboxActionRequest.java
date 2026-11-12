package com.collegeconnect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NotificationOutboxActionRequest(
        @NotNull Boolean confirmedNeverAccepted,
        @NotBlank @Size(max = 1000) String reason) {
}

package com.collegeconnect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NotificationReconciliationRequest(
        @NotBlank @Pattern(regexp = "CONFIRMED_NEVER_ACCEPTED|CONFIRMED_ACCEPTED") String outcome,
        @Size(max = 190) String providerMessageId,
        @NotBlank @Size(max = 1000) String reason) {}

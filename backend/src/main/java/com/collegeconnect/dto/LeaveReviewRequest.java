package com.collegeconnect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LeaveReviewRequest(
        @NotBlank @Pattern(regexp = "UNDER_REVIEW|APPROVED|REJECTED", flags = Pattern.Flag.CASE_INSENSITIVE)
        String decision,
        @Size(max = 2000) String comment) {
}

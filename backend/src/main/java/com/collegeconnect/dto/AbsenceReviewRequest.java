package com.collegeconnect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AbsenceReviewRequest(
        @NotBlank @Pattern(regexp = "ACCEPTED|REJECTED|MORE_INFO", flags = Pattern.Flag.CASE_INSENSITIVE)
        String decision,
        @Size(max = 2000) String comments) {
}

package com.collegeconnect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SeeAssessmentCreateRequest(
        @NotNull Long offeringId,
        @NotBlank @Size(max = 100) String name) {
}

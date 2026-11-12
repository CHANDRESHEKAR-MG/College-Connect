package com.collegeconnect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AbsenceExplanationRequest(@NotBlank @Size(max = 2000) String explanation) {
}

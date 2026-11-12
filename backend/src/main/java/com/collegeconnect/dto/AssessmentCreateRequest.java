package com.collegeconnect.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AssessmentCreateRequest(
        @NotNull Long offeringId,
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 5, fraction = 2) BigDecimal maximumMarks) {
}

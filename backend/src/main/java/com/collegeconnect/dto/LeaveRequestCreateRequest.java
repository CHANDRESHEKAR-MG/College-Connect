package com.collegeconnect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record LeaveRequestCreateRequest(
        @NotNull Long studentId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Size(max = 80) String reasonCategory,
        @NotBlank @Size(max = 2000) String reason) {
}

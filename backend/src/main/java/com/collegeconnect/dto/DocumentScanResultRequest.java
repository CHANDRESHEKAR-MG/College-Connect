package com.collegeconnect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DocumentScanResultRequest(
        @NotBlank @Pattern(regexp = "CLEAN|REJECTED") String result,
        @NotBlank @Size(max = 1000) String reason) {
}

package com.collegeconnect.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AttendanceSubmissionRequest(
        @NotEmpty List<@NotNull @Valid AttendanceMarkRequest> records) {
}

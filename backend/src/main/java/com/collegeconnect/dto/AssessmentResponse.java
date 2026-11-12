package com.collegeconnect.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AssessmentResponse(
        Long assessmentId,
        Long offeringId,
        String name,
        BigDecimal maximumMarks,
        String publicationStatus,
        Instant publishedAt) {
}

package com.collegeconnect.dto;

public record PublishedMarkResponse(
        String assessmentType,
        Long assessmentId,
        String assessmentName,
        String subjectCode,
        String subjectName,
        String value,
        Integer publishedRevision) {
}

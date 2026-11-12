package com.collegeconnect.dto;

import java.time.Instant;

public record SupportingDocumentResponse(
        Long documentId,
        String fileName,
        String mediaType,
        long fileSizeBytes,
        String scanStatus,
        Instant uploadedAt) {
}

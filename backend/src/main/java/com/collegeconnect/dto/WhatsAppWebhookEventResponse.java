package com.collegeconnect.dto;

import java.time.Instant;

public record WhatsAppWebhookEventResponse(
        Long id,
        String eventType,
        String processingStatus,
        String payloadHash,
        Long parentId,
        Long attemptId,
        String processingError,
        Instant receivedAt) {}

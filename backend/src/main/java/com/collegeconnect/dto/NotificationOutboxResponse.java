package com.collegeconnect.dto;

import java.time.Instant;

public record NotificationOutboxResponse(
        Long eventId,
        Long studentId,
        Long parentId,
        String sourceType,
        Long sourceId,
        String templateName,
        String status,
        Instant createdAt) {
}

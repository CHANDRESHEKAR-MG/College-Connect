package com.collegeconnect.dto;

import java.time.Instant;

public record DocumentAuditEntryResponse(
        Long auditId,
        Long actorAccountId,
        String actorRole,
        String action,
        String reason,
        Instant occurredAt) {}

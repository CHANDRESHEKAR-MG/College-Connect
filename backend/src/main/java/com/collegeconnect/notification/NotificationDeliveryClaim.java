package com.collegeconnect.notification;

public record NotificationDeliveryClaim(
        Long eventId,
        int attemptNumber,
        String recipient,
        String templateName,
        String payloadJson,
        String correlationId) {}

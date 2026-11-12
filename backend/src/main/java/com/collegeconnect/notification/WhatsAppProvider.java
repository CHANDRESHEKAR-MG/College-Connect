package com.collegeconnect.notification;

public interface WhatsAppProvider {

    SendResult send(SendRequest request);

    record SendRequest(
            String recipient,
            String templateName,
            String payloadJson,
            String correlationId) {}

    record SendResult(String providerMessageId, Integer httpStatus) {}
}

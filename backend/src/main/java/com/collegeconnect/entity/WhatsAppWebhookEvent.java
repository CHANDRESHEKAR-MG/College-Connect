package com.collegeconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "whatsapp_webhook_event")
public class WhatsAppWebhookEvent extends BaseEntity {

    @Column(name = "provider_event_id", unique = true, length = 190)
    private String providerEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ParentProfile parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id")
    private NotificationAttempt attempt;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "payload_hash", length = 128)
    private String payloadHash;

    @Column(name = "payload_json", length = 12000)
    private String payloadJson;

    @Column(name = "processing_status", nullable = false, length = 30)
    private String processingStatus;

    @Column(name = "processing_attempts", nullable = false)
    private int processingAttempts;

    @Column(name = "processing_error", length = 1000)
    private String processingError;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected WhatsAppWebhookEvent() {
    }

    public WhatsAppWebhookEvent(String providerEventId, String eventType, Instant receivedAt) {
        this.providerEventId = providerEventId;
        this.eventType = eventType;
        this.receivedAt = receivedAt;
        this.processingStatus = "RECEIVED";
    }

    public WhatsAppWebhookEvent(
            String providerEventId, String eventType, String payloadHash, String payloadJson, Instant receivedAt) {
        this(providerEventId, eventType, receivedAt);
        this.payloadHash = payloadHash;
        this.payloadJson = payloadJson;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public NotificationAttempt getAttempt() {
        return attempt;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public String getProcessingError() {
        return processingError;
    }

    public ParentProfile getParent() {
        return parent;
    }

    public int getProcessingAttempts() {
        return processingAttempts;
    }

    public void markProcessed(Instant processedAt) {
        this.processingStatus = "PROCESSED";
        this.processingError = null;
        this.processingAttempts++;
        this.processedAt = processedAt;
    }

    public void markProcessingStarted() {
        this.processingStatus = "PROCESSING";
    }

    public void attachParent(ParentProfile parent) {
        this.parent = parent;
    }

    public void attachAttempt(NotificationAttempt attempt) {
        this.attempt = attempt;
    }

    public void markReconciliationRequired(String reason, Instant at) {
        this.processingStatus = "RECONCILIATION_REQUIRED";
        this.processingError = reason;
        this.processedAt = at;
        this.processingAttempts++;
    }

    public void markProcessingFailure(String reason, Instant failedAt) {
        this.processingAttempts++;
        this.processingError = reason;
        if (processingAttempts >= 5) {
            this.processingStatus = "PROCESSING_FAILED";
            this.processedAt = failedAt;
        } else {
            this.processingStatus = "RECEIVED";
        }
    }
}

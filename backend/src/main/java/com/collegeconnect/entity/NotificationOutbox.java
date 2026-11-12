package com.collegeconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "notification_outbox")
public class NotificationOutbox extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ParentProfile parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_event_id")
    private NotificationOutbox originalEvent;

    @Column(name = "source_type", nullable = false, length = 60)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "template_name", nullable = false, length = 120)
    private String templateName;

    @Column(name = "payload_json", nullable = false, length = 4000)
    private String payloadJson;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 190)
    private String idempotencyKey;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationOutbox() {
    }

    public NotificationOutbox(
            StudentProfile student,
            ParentProfile parent,
            String sourceType,
            Long sourceId,
            String templateName,
            String payloadJson,
            String idempotencyKey,
            String status,
            Instant createdAt) {
        this.student = student;
        this.parent = parent;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.templateName = templateName;
        this.payloadJson = payloadJson;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public StudentProfile getStudent() {
        return student;
    }

    public ParentProfile getParent() {
        return parent;
    }

    public String getStatus() {
        return status;
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getSourceType() {
        return sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getLeaseUntil() {
        return leaseUntil;
    }

    public void claim(int attemptCount, Instant leaseUntil, Instant updatedAt) {
        this.status = "PROCESSING";
        this.attemptCount = attemptCount;
        this.leaseUntil = leaseUntil;
        this.updatedAt = updatedAt;
    }

    public void markSent(Instant updatedAt) {
        this.status = "SENT";
        this.nextAttemptAt = null;
        this.leaseUntil = null;
        this.updatedAt = updatedAt;
    }

    public void markDelivered(Instant updatedAt) {
        this.status = "DELIVERED";
        this.nextAttemptAt = null;
        this.leaseUntil = null;
        this.updatedAt = updatedAt;
    }

    public void markRead(Instant updatedAt) {
        this.status = "READ";
        this.nextAttemptAt = null;
        this.leaseUntil = null;
        this.updatedAt = updatedAt;
    }

    public void markDeliveryFailed(Instant updatedAt) {
        this.status = "DELIVERY_FAILED";
        this.nextAttemptAt = null;
        this.leaseUntil = null;
        this.updatedAt = updatedAt;
    }

    public void markRetryable(Instant nextAttemptAt, Instant updatedAt) {
        this.status = "FAILED_RETRYABLE";
        this.nextAttemptAt = nextAttemptAt;
        this.leaseUntil = null;
        this.updatedAt = updatedAt;
    }

    public void markDeadLetter(Instant updatedAt) {
        this.status = "FAILED_DEAD_LETTER";
        this.nextAttemptAt = null;
        this.leaseUntil = null;
        this.updatedAt = updatedAt;
    }

    public void markAmbiguous(Instant updatedAt) {
        this.status = "AMBIGUOUS_DELIVERY";
        this.nextAttemptAt = null;
        this.leaseUntil = null;
        this.updatedAt = updatedAt;
    }

    public void skip(String reasonStatus, Instant updatedAt) {
        this.status = reasonStatus;
        this.nextAttemptAt = null;
        this.leaseUntil = null;
        this.updatedAt = updatedAt;
    }

    public void queueConfirmedRetry(Instant updatedAt) {
        this.status = "PENDING";
        this.nextAttemptAt = null;
        this.leaseUntil = null;
        this.updatedAt = updatedAt;
    }

    public boolean cancelIfNotAccepted(Instant updatedAt) {
        if (!"PENDING".equals(status) && !"FAILED_RETRYABLE".equals(status)) {
            return false;
        }
        this.status = "CANCELLED";
        this.nextAttemptAt = null;
        this.leaseUntil = null;
        this.updatedAt = updatedAt;
        return true;
    }

    public void linkToOriginal(NotificationOutbox originalEvent) {
        this.originalEvent = originalEvent;
    }
}

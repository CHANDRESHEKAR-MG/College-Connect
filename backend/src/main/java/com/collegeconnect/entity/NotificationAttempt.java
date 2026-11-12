package com.collegeconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "notification_attempt",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_attempt_number",
                columnNames = {"event_id", "attempt_number"}))
public class NotificationAttempt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private NotificationOutbox event;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "request_correlation_id", length = 190)
    private String requestCorrelationId;

    @Column(name = "provider_message_id", unique = true, length = 190)
    private String providerMessageId;

    @Column(nullable = false, length = 40)
    private String result;

    @Column(name = "delivery_status", length = 30)
    private String deliveryStatus;

    @Column(name = "provider_error_code", length = 120)
    private String providerErrorCode;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected NotificationAttempt() {
    }

    public NotificationAttempt(
            NotificationOutbox event,
            int attemptNumber,
            String requestCorrelationId,
            String result,
            Instant startedAt) {
        this.event = event;
        this.attemptNumber = attemptNumber;
        this.requestCorrelationId = requestCorrelationId;
        this.result = result;
        this.startedAt = startedAt;
    }

    public NotificationOutbox getEvent() {
        return event;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public String getRequestCorrelationId() {
        return requestCorrelationId;
    }

    public String getResult() {
        return result;
    }

    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void accepted(String providerMessageId, Integer httpStatus, Instant finishedAt) {
        this.providerMessageId = providerMessageId;
        this.httpStatus = httpStatus;
        this.result = "ACCEPTED";
        if (this.deliveryStatus == null || "PROCESSING".equals(this.deliveryStatus)) {
            this.deliveryStatus = "SENT";
        }
        this.finishedAt = finishedAt;
    }

    public void failed(
            String result,
            String errorCode,
            Integer httpStatus,
            String reason,
            Instant finishedAt) {
        this.result = result;
        this.providerErrorCode = errorCode;
        this.httpStatus = httpStatus;
        this.failureReason = reason;
        this.finishedAt = finishedAt;
    }

    public void updateDeliveryStatus(
            String status, String errorCode, String reason, Instant finishedAt) {
        this.deliveryStatus = status;
        this.providerErrorCode = errorCode;
        this.failureReason = reason;
        this.finishedAt = finishedAt;
    }
}

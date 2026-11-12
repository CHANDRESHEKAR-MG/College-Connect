package com.collegeconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "in_app_notification")
public class InAppNotification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_account_id", nullable = false)
    private UserAccount recipient;

    @Column(name = "source_type", nullable = false, length = 60)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(nullable = false, length = 240)
    private String title;

    @Column(length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected InAppNotification() {
    }

    public InAppNotification(
            UserAccount recipient, String sourceType, Long sourceId, String title, Instant createdAt) {
        this.recipient = recipient;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.title = title;
        this.createdAt = createdAt;
    }

    public UserAccount getRecipient() {
        return recipient;
    }

    public String getTitle() {
        return title;
    }

    public Instant getReadAt() {
        return readAt;
    }
}

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
        name = "notification_default_preference",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_default_category",
                columnNames = "category"))
public class NotificationDefaultPreference extends BaseEntity {

    @Column(nullable = false, length = 60)
    private String category;

    @Column(nullable = false)
    private boolean enabled;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_account_id")
    private UserAccount updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationDefaultPreference() {}

    public NotificationDefaultPreference(String category, boolean enabled, Instant updatedAt) {
        this.category = category;
        this.enabled = enabled;
        this.updatedAt = updatedAt;
    }

    public String getCategory() {
        return category;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(boolean enabled, UserAccount actor, Instant updatedAt) {
        this.enabled = enabled;
        this.updatedBy = actor;
        this.updatedAt = updatedAt;
    }
}

package com.collegeconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "notification_preference",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_preference_parent_category",
                columnNames = {"parent_id", "category"}))
public class NotificationPreference extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_id", nullable = false)
    private ParentProfile parent;

    @Column(nullable = false, length = 60)
    private String category;

    @Column(nullable = false)
    private boolean enabled;

    protected NotificationPreference() {
    }

    public NotificationPreference(ParentProfile parent, String category, boolean enabled) {
        this.parent = parent;
        this.category = category;
        this.enabled = enabled;
    }

    public ParentProfile getParent() {
        return parent;
    }

    public String getCategory() {
        return category;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}

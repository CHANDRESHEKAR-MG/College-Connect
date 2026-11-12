package com.collegeconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "whatsapp_consent_event")
public class WhatsAppConsentEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_id", nullable = false)
    private ParentProfile parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_student_link_id")
    private ParentStudentLink parentStudentLink;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_account_id")
    private UserAccount actor;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(nullable = false, length = 40)
    private String method;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected WhatsAppConsentEvent() {
    }

    public WhatsAppConsentEvent(
            ParentProfile parent,
            ParentStudentLink parentStudentLink,
            UserAccount actor,
            String action,
            String method,
            Instant recordedAt) {
        this.parent = parent;
        this.parentStudentLink = parentStudentLink;
        this.actor = actor;
        this.action = action;
        this.method = method;
        this.recordedAt = recordedAt;
    }

    public ParentProfile getParent() {
        return parent;
    }

    public String getAction() {
        return action;
    }

    public String getMethod() {
        return method;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}

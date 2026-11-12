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
        name = "see_mark_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_see_mark_history_revision",
                columnNames = {"see_mark_id", "revision_number"}))
public class SeeMarkHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "see_mark_id", nullable = false)
    private SeeMark mark;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_account_id", nullable = false)
    private UserAccount actor;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(name = "previous_value", length = 32)
    private String previousValue;

    @Column(name = "new_value", nullable = false, length = 32)
    private String newValue;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected SeeMarkHistory() {
    }

    public SeeMarkHistory(
            SeeMark mark,
            UserAccount actor,
            int revisionNumber,
            String previousValue,
            String newValue,
            String reason,
            Instant changedAt) {
        this.mark = mark;
        this.actor = actor;
        this.revisionNumber = revisionNumber;
        this.previousValue = previousValue;
        this.newValue = newValue;
        this.reason = reason;
        this.changedAt = changedAt;
    }

    public SeeMark getMark() {
        return mark;
    }

    public int getRevisionNumber() {
        return revisionNumber;
    }

    public String getNewValue() {
        return newValue;
    }
}

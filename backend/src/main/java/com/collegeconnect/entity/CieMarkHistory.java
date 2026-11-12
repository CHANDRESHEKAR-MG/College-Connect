package com.collegeconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "cie_mark_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cie_mark_history_revision",
                columnNames = {"cie_mark_id", "revision_number"}))
public class CieMarkHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cie_mark_id", nullable = false)
    private CieMark mark;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_account_id", nullable = false)
    private UserAccount actor;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(name = "previous_marks", precision = 7, scale = 2)
    private BigDecimal previousMarks;

    @Column(name = "new_marks", nullable = false, precision = 7, scale = 2)
    private BigDecimal newMarks;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected CieMarkHistory() {
    }

    public CieMarkHistory(
            CieMark mark,
            UserAccount actor,
            int revisionNumber,
            BigDecimal previousMarks,
            BigDecimal newMarks,
            String reason,
            Instant changedAt) {
        this.mark = mark;
        this.actor = actor;
        this.revisionNumber = revisionNumber;
        this.previousMarks = previousMarks;
        this.newMarks = newMarks;
        this.reason = reason;
        this.changedAt = changedAt;
    }

    public CieMark getMark() {
        return mark;
    }

    public int getRevisionNumber() {
        return revisionNumber;
    }

    public BigDecimal getNewMarks() {
        return newMarks;
    }
}

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
        name = "attendance_correction",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_attendance_correction_sequence",
                columnNames = {"attendance_id", "correction_sequence"}))
public class AttendanceCorrection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attendance_id", nullable = false)
    private AttendanceRecord attendance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_account_id", nullable = false)
    private UserAccount actor;

    @Column(name = "actor_role", nullable = false, length = 30)
    private String actorRole;

    @Column(name = "correction_sequence", nullable = false)
    private int correctionSequence;

    @Column(name = "previous_status", nullable = false, length = 30)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 30)
    private String newStatus;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AttendanceCorrection() {
    }

    public AttendanceCorrection(
            AttendanceRecord attendance,
            UserAccount actor,
            String actorRole,
            int correctionSequence,
            String previousStatus,
            String newStatus,
            String reason,
            String comment,
            Instant createdAt) {
        this.attendance = attendance;
        this.actor = actor;
        this.actorRole = actorRole;
        this.correctionSequence = correctionSequence;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reason = reason;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public AttendanceRecord getAttendance() {
        return attendance;
    }

    public int getCorrectionSequence() {
        return correctionSequence;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public String getReason() {
        return reason;
    }

    public String getComment() {
        return comment;
    }

    public UserAccount getActor() {
        return actor;
    }

    public String getActorRole() {
        return actorRole;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

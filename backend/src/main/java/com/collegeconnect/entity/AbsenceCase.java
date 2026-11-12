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
        name = "absence_case",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_absence_case_attendance",
                columnNames = "attendance_id"))
public class AbsenceCase extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attendance_id", nullable = false)
    private AttendanceRecord attendance;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(length = 2000)
    private String explanation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_account_id")
    private UserAccount reviewer;

    @Column(name = "review_comments", length = 2000)
    private String reviewComments;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AbsenceCase() {
    }

    public AbsenceCase(AttendanceRecord attendance, String status, Instant createdAt) {
        this.attendance = attendance;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public AttendanceRecord getAttendance() {
        return attendance;
    }

    public String getStatus() {
        return status;
    }

    public String getExplanation() {
        return explanation;
    }

    public void reopenForCorrection(Instant updatedAt) {
        this.status = "UNEXPLAINED";
        this.explanation = null;
        this.reviewer = null;
        this.reviewComments = null;
        this.updatedAt = updatedAt;
    }

    public void closeForCorrection(Instant updatedAt) {
        this.status = "CLOSED_CORRECTED";
        this.updatedAt = updatedAt;
    }

    public void submitExplanation(String explanation, Instant updatedAt) {
        this.explanation = explanation;
        this.status = "UNDER_REVIEW";
        this.updatedAt = updatedAt;
    }

    public void review(UserAccount reviewer, String decision, String comments, Instant reviewedAt) {
        this.reviewer = reviewer;
        this.status = decision;
        this.reviewComments = comments;
        this.updatedAt = reviewedAt;
    }

    public UserAccount getReviewer() {
        return reviewer;
    }

    public String getReviewComments() {
        return reviewComments;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

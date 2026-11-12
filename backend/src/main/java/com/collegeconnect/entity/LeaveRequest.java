package com.collegeconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "leave_request")
public class LeaveRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_account_id", nullable = false)
    private UserAccount requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_account_id")
    private UserAccount reviewer;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "reason_category", length = 80)
    private String reasonCategory;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "review_comment", length = 2000)
    private String reviewComment;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected LeaveRequest() {
    }

    public LeaveRequest(
            StudentProfile student,
            UserAccount requester,
            LocalDate startDate,
            LocalDate endDate,
            String reasonCategory,
            String reason,
            String status,
            Instant submittedAt) {
        this.student = student;
        this.requester = requester;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reasonCategory = reasonCategory;
        this.reason = reason;
        this.status = status;
        this.submittedAt = submittedAt;
    }

    public StudentProfile getStudent() {
        return student;
    }

    public UserAccount getRequester() {
        return requester;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getStatus() {
        return status;
    }

    public String getReasonCategory() {
        return reasonCategory;
    }

    public String getReason() {
        return reason;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void review(UserAccount reviewer, String decision, String comment, Instant reviewedAt) {
        this.reviewer = reviewer;
        this.status = decision;
        this.reviewComment = comment;
        this.reviewedAt = reviewedAt;
    }

    public void cancel() {
        this.status = "CANCELLED";
    }
}

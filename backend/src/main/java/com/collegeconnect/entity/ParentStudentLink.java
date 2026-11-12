package com.collegeconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "parent_student_link")
public class ParentStudentLink extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_id", nullable = false)
    private ParentProfile parent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_account_id")
    private UserAccount verifiedBy;

    @Column(name = "verification_status", nullable = false, length = 30)
    private String verificationStatus;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ParentStudentLink() {
    }

    public ParentStudentLink(ParentProfile parent, StudentProfile student, String verificationStatus) {
        this.parent = parent;
        this.student = student;
        this.verificationStatus = verificationStatus;
    }

    public ParentProfile getParent() {
        return parent;
    }

    public StudentProfile getStudent() {
        return student;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public boolean isActiveVerified() {
        return "VERIFIED".equalsIgnoreCase(verificationStatus)
                && activatedAt != null
                && revokedAt == null;
    }
}

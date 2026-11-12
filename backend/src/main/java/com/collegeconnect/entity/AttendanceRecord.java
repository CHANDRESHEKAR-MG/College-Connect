package com.collegeconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(
        name = "attendance_record",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_attendance_session_student",
                columnNames = {"session_id", "student_id"}))
public class AttendanceRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ClassSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by_professor_id", nullable = false)
    private ProfessorProfile submittedBy;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "source_action", nullable = false, length = 40)
    private String sourceAction;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    protected AttendanceRecord() {
    }

    public AttendanceRecord(
            ClassSession session,
            StudentProfile student,
            ProfessorProfile submittedBy,
            String status,
            String sourceAction,
            Instant submittedAt) {
        this.session = session;
        this.student = student;
        this.submittedBy = submittedBy;
        this.status = status;
        this.sourceAction = sourceAction;
        this.submittedAt = submittedAt;
    }

    public ClassSession getSession() {
        return session;
    }

    public StudentProfile getStudent() {
        return student;
    }

    public ProfessorProfile getSubmittedBy() {
        return submittedBy;
    }

    public String getStatus() {
        return status;
    }

    public String getSourceAction() {
        return sourceAction;
    }

    public void correctStatus(String status) {
        this.status = status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}

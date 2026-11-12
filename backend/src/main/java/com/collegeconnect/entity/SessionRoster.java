package com.collegeconnect.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "session_roster",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_session_roster_session_student",
                columnNames = {"session_id", "student_id"}))
public class SessionRoster extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ClassSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    protected SessionRoster() {
    }

    public SessionRoster(ClassSession session, StudentProfile student) {
        this.session = session;
        this.student = student;
    }

    public ClassSession getSession() {
        return session;
    }

    public StudentProfile getStudent() {
        return student;
    }
}

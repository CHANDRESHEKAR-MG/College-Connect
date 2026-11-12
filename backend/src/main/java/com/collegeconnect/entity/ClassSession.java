package com.collegeconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "class_session")
public class ClassSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id")
    private TimetableSlot slot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private ProfessorAssignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "substitute_professor_id")
    private ProfessorProfile substituteProfessor;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(nullable = false, length = 30)
    private String status;

    protected ClassSession() {
    }

    public ClassSession(
            TimetableSlot slot,
            ProfessorAssignment assignment,
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime,
            String status) {
        this.slot = slot;
        this.assignment = assignment;
        this.sessionDate = sessionDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
    }

    public TimetableSlot getSlot() {
        return slot;
    }

    public ProfessorAssignment getAssignment() {
        return assignment;
    }

    public ProfessorProfile getSubstituteProfessor() {
        return substituteProfessor;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public String getStatus() {
        return status;
    }

    public void markSubmitted() {
        this.status = "SUBMITTED";
    }
}

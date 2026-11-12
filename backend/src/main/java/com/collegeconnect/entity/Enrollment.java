package com.collegeconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "enrollment")
public class Enrollment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offering_id", nullable = false)
    private SubjectOffering offering;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(nullable = false, length = 30)
    private String status;

    protected Enrollment() {
    }

    public Enrollment(
            StudentProfile student,
            SubjectOffering offering,
            LocalDate validFrom,
            LocalDate validTo,
            String status) {
        this.student = student;
        this.offering = offering;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.status = status;
    }

    public StudentProfile getStudent() {
        return student;
    }

    public SubjectOffering getOffering() {
        return offering;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }
}

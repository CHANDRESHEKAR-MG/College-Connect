package com.collegeconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "professor_assignment")
public class ProfessorAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professor_id", nullable = false)
    private ProfessorProfile professor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offering_id", nullable = false)
    private SubjectOffering offering;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(nullable = false, length = 30)
    private String status;

    protected ProfessorAssignment() {
    }

    public ProfessorAssignment(ProfessorProfile professor, SubjectOffering offering, String status) {
        this.professor = professor;
        this.offering = offering;
        this.status = status;
    }

    public ProfessorProfile getProfessor() {
        return professor;
    }

    public SubjectOffering getOffering() {
        return offering;
    }

    public String getStatus() {
        return status;
    }
}

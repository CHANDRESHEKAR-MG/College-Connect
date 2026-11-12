package com.collegeconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "subject_offering")
public class SubjectOffering extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private Section section;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @Column(nullable = false, length = 30)
    private String status;

    protected SubjectOffering() {
    }

    public SubjectOffering(Subject subject, Section section, Semester semester, String status) {
        this.subject = subject;
        this.section = section;
        this.semester = semester;
        this.status = status;
    }

    public Subject getSubject() {
        return subject;
    }

    public Section getSection() {
        return section;
    }

    public Semester getSemester() {
        return semester;
    }

    public String getStatus() {
        return status;
    }
}

package com.collegeconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "section")
public class Section extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "programme_id", nullable = false)
    private Programme programme;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @Column(nullable = false, length = 40)
    private String name;

    protected Section() {
    }

    public Section(Programme programme, Semester semester, String name) {
        this.programme = programme;
        this.semester = semester;
        this.name = name;
    }

    public Programme getProgramme() {
        return programme;
    }

    public Semester getSemester() {
        return semester;
    }

    public String getName() {
        return name;
    }
}

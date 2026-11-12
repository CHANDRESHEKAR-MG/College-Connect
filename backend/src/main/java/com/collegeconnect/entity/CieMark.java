package com.collegeconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;

@Entity
@Table(
        name = "cie_mark",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cie_mark_assessment_student",
                columnNames = {"assessment_id", "student_id"}))
public class CieMark extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false)
    private CieAssessment assessment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal marks;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(name = "published_revision_number")
    private Integer publishedRevisionNumber;

    @Version
    @Column(nullable = false)
    private long version;

    protected CieMark() {
    }

    public CieMark(CieAssessment assessment, StudentProfile student, BigDecimal marks) {
        this.assessment = assessment;
        this.student = student;
        this.marks = marks;
        this.revisionNumber = 1;
    }

    public CieAssessment getAssessment() {
        return assessment;
    }

    public StudentProfile getStudent() {
        return student;
    }

    public BigDecimal getMarks() {
        return marks;
    }

    public int getRevisionNumber() {
        return revisionNumber;
    }

    public Integer getPublishedRevisionNumber() {
        return publishedRevisionNumber;
    }

    public void revise(BigDecimal marks) {
        this.marks = marks;
        this.revisionNumber++;
    }

    public void publishCurrentRevision() {
        this.publishedRevisionNumber = revisionNumber;
    }
}

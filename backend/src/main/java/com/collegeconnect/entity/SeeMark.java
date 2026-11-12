package com.collegeconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
        name = "see_mark",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_see_mark_assessment_student",
                columnNames = {"see_assessment_id", "student_id"}))
public class SeeMark extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "see_assessment_id", nullable = false)
    private SeeAssessment assessment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Column(name = "result_value", nullable = false, length = 32)
    private String resultValue;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(name = "published_revision_number")
    private Integer publishedRevisionNumber;

    @Version
    @Column(nullable = false)
    private long version;

    protected SeeMark() {
    }

    public SeeMark(SeeAssessment assessment, StudentProfile student, String resultValue) {
        this.assessment = assessment;
        this.student = student;
        this.resultValue = resultValue;
        this.revisionNumber = 1;
    }

    public SeeAssessment getAssessment() {
        return assessment;
    }

    public StudentProfile getStudent() {
        return student;
    }

    public String getResultValue() {
        return resultValue;
    }

    public int getRevisionNumber() {
        return revisionNumber;
    }

    public Integer getPublishedRevisionNumber() {
        return publishedRevisionNumber;
    }

    public void revise(String resultValue) {
        this.resultValue = resultValue;
        this.revisionNumber++;
    }

    public void publishCurrentRevision() {
        this.publishedRevisionNumber = revisionNumber;
    }
}

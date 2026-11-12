package com.collegeconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cie_assessment")
public class CieAssessment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offering_id", nullable = false)
    private SubjectOffering offering;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "maximum_marks", nullable = false, precision = 7, scale = 2)
    private BigDecimal maximumMarks;

    @Column(name = "publication_status", nullable = false, length = 30)
    private String publicationStatus;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected CieAssessment() {
    }

    public CieAssessment(
            SubjectOffering offering, String name, BigDecimal maximumMarks, String publicationStatus) {
        this.offering = offering;
        this.name = name;
        this.maximumMarks = maximumMarks;
        this.publicationStatus = publicationStatus;
    }

    public SubjectOffering getOffering() {
        return offering;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getMaximumMarks() {
        return maximumMarks;
    }

    public String getPublicationStatus() {
        return publicationStatus;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void publish(Instant publishedAt) {
        this.publicationStatus = "PUBLISHED";
        this.publishedAt = publishedAt;
    }
}

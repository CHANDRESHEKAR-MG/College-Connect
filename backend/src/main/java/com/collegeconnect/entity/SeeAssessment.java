package com.collegeconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "see_assessment")
public class SeeAssessment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offering_id", nullable = false)
    private SubjectOffering offering;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "publication_status", nullable = false, length = 30)
    private String publicationStatus;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected SeeAssessment() {
    }

    public SeeAssessment(SubjectOffering offering, String name, String publicationStatus) {
        this.offering = offering;
        this.name = name;
        this.publicationStatus = publicationStatus;
    }

    public SubjectOffering getOffering() {
        return offering;
    }

    public String getName() {
        return name;
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

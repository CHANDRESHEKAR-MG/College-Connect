package com.collegeconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "student_profile")
public class StudentProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private UserAccount account;

    @Column(name = "college_identifier", nullable = false, unique = true, length = 60)
    private String collegeIdentifier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "programme_id", nullable = false)
    private Programme programme;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private Section section;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Column(nullable = false, length = 30)
    private String status;

    protected StudentProfile() {
    }

    public StudentProfile(
            UserAccount account,
            String collegeIdentifier,
            Programme programme,
            Section section,
            String displayName,
            String status) {
        this.account = account;
        this.collegeIdentifier = collegeIdentifier;
        this.programme = programme;
        this.section = section;
        this.displayName = displayName;
        this.status = status;
    }

    public UserAccount getAccount() {
        return account;
    }

    public String getCollegeIdentifier() {
        return collegeIdentifier;
    }

    public Programme getProgramme() {
        return programme;
    }

    public Section getSection() {
        return section;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getStatus() {
        return status;
    }
}

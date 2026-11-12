package com.collegeconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "admin_profile")
public class AdminProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private UserAccount account;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    protected AdminProfile() {
    }

    public AdminProfile(UserAccount account, String displayName) {
        this.account = account;
        this.displayName = displayName;
    }

    public UserAccount getAccount() {
        return account;
    }

    public String getDisplayName() {
        return displayName;
    }
}

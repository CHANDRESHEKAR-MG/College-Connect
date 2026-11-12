package com.collegeconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_account")
public class UserAccount extends BaseEntity {

    @Column(name = "login_identifier", nullable = false, unique = true, length = 190)
    private String loginIdentifier;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 30)
    private String role;

    @Column(nullable = false, length = 30)
    private String status;

    protected UserAccount() {
    }

    public UserAccount(String loginIdentifier, String passwordHash, String role, String status) {
        this.loginIdentifier = loginIdentifier;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
    }

    public String getLoginIdentifier() {
        return loginIdentifier;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }
}

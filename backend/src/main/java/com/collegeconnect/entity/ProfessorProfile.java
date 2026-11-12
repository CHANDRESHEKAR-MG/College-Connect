package com.collegeconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "professor_profile")
public class ProfessorProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private UserAccount account;

    @Column(name = "employee_identifier", nullable = false, unique = true, length = 60)
    private String employeeIdentifier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    protected ProfessorProfile() {
    }

    public ProfessorProfile(
            UserAccount account, String employeeIdentifier, Department department, String displayName) {
        this.account = account;
        this.employeeIdentifier = employeeIdentifier;
        this.department = department;
        this.displayName = displayName;
    }

    public UserAccount getAccount() {
        return account;
    }

    public String getEmployeeIdentifier() {
        return employeeIdentifier;
    }

    public Department getDepartment() {
        return department;
    }

    public String getDisplayName() {
        return displayName;
    }
}

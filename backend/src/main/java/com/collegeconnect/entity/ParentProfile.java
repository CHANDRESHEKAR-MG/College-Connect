package com.collegeconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "parent_profile")
public class ParentProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private UserAccount account;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Column(name = "verified_mobile", unique = true, length = 24)
    private String verifiedMobile;

    @Column(name = "mobile_verified_at")
    private Instant mobileVerifiedAt;

    @Column(name = "whatsapp_opt_in", nullable = false)
    private boolean whatsappOptIn;

    @Column(name = "opt_in_recorded_at")
    private Instant optInRecordedAt;

    protected ParentProfile() {
    }

    public ParentProfile(UserAccount account, String displayName) {
        this.account = account;
        this.displayName = displayName;
    }

    public UserAccount getAccount() {
        return account;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getVerifiedMobile() {
        return verifiedMobile;
    }

    public boolean isWhatsappOptIn() {
        return whatsappOptIn;
    }

    public boolean hasVerifiedMobile() {
        return verifiedMobile != null && mobileVerifiedAt != null;
    }

    public void recordWhatsappConsent(boolean optedIn, Instant recordedAt) {
        this.whatsappOptIn = optedIn;
        this.optInRecordedAt = optedIn ? recordedAt : null;
    }
}

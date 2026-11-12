package com.collegeconnect.security;

import com.collegeconnect.entity.UserAccount;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class UserAccountPrincipal implements UserDetails {

    private final Long id;
    private final String loginIdentifier;
    private final String passwordHash;
    private final Role role;
    private final boolean enabled;

    public UserAccountPrincipal(UserAccount account, Role role) {
        this.id = account.getId();
        this.loginIdentifier = account.getLoginIdentifier();
        this.passwordHash = account.getPasswordHash();
        this.role = role;
        this.enabled = "ACTIVE".equalsIgnoreCase(account.getStatus());
    }

    public Long getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return loginIdentifier;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}

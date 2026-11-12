package com.collegeconnect.security;

import com.collegeconnect.entity.UserAccount;
import com.collegeconnect.repository.UserAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserAccountDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;

    public UserAccountDetailsService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String loginIdentifier) {
        UserAccount account = userAccountRepository.findByLoginIdentifier(loginIdentifier)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));

        try {
            return new UserAccountPrincipal(account, Role.fromDatabaseValue(account.getRole()));
        } catch (IllegalArgumentException exception) {
            throw new UsernameNotFoundException("Invalid credentials", exception);
        }
    }
}

package com.collegeconnect.repository;

import com.collegeconnect.entity.UserAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByLoginIdentifier(String loginIdentifier);
}

package com.collegeconnect.repository;

import com.collegeconnect.entity.ParentProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParentProfileRepository extends JpaRepository<ParentProfile, Long> {

    Optional<ParentProfile> findByAccount_Id(Long accountId);

    Optional<ParentProfile> findByVerifiedMobile(String verifiedMobile);
}

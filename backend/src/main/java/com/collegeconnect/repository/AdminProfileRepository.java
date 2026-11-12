package com.collegeconnect.repository;

import com.collegeconnect.entity.AdminProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminProfileRepository extends JpaRepository<AdminProfile, Long> {

    Optional<AdminProfile> findByAccount_Id(Long accountId);
}

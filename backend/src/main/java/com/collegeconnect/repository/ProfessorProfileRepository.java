package com.collegeconnect.repository;

import com.collegeconnect.entity.ProfessorProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfessorProfileRepository extends JpaRepository<ProfessorProfile, Long> {

    Optional<ProfessorProfile> findByAccount_Id(Long accountId);
}

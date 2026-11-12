package com.collegeconnect.repository;

import com.collegeconnect.entity.ProfessorAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfessorAssignmentRepository extends JpaRepository<ProfessorAssignment, Long> {

    boolean existsByOffering_IdAndProfessor_Account_IdAndStatusIgnoreCase(
            Long offeringId, Long accountId, String status);
}

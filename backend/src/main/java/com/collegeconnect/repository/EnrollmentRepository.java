package com.collegeconnect.repository;

import com.collegeconnect.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    boolean existsByStudent_IdAndOffering_IdAndStatusIgnoreCase(
            Long studentId, Long offeringId, String status);
}

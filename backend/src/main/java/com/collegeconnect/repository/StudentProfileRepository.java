package com.collegeconnect.repository;

import com.collegeconnect.entity.StudentProfile;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

    Optional<StudentProfile> findByAccount_Id(Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StudentProfile s where s.id = :studentId")
    Optional<StudentProfile> findByIdForUpdate(@Param("studentId") Long studentId);
}

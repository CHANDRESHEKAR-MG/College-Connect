package com.collegeconnect.repository;

import com.collegeconnect.entity.CieMark;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CieMarkRepository extends JpaRepository<CieMark, Long> {

    Optional<CieMark> findByAssessment_IdAndStudent_Id(Long assessmentId, Long studentId);

    List<CieMark> findByAssessment_Id(Long assessmentId);

    List<CieMark> findByStudent_Id(Long studentId);
}

package com.collegeconnect.repository;

import com.collegeconnect.entity.SeeMark;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeeMarkRepository extends JpaRepository<SeeMark, Long> {

    Optional<SeeMark> findByAssessment_IdAndStudent_Id(Long assessmentId, Long studentId);

    List<SeeMark> findByAssessment_Id(Long assessmentId);

    List<SeeMark> findByStudent_Id(Long studentId);
}

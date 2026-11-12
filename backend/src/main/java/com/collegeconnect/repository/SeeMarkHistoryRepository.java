package com.collegeconnect.repository;

import com.collegeconnect.entity.SeeMarkHistory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeeMarkHistoryRepository extends JpaRepository<SeeMarkHistory, Long> {

    Optional<SeeMarkHistory> findByMark_IdAndRevisionNumber(Long markId, int revisionNumber);
}

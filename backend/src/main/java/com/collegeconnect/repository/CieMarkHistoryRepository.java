package com.collegeconnect.repository;

import com.collegeconnect.entity.CieMarkHistory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CieMarkHistoryRepository extends JpaRepository<CieMarkHistory, Long> {

    Optional<CieMarkHistory> findByMark_IdAndRevisionNumber(Long markId, int revisionNumber);
}

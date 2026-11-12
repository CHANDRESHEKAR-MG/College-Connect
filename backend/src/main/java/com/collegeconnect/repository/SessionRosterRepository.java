package com.collegeconnect.repository;

import com.collegeconnect.entity.SessionRoster;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRosterRepository extends JpaRepository<SessionRoster, Long> {

    List<SessionRoster> findBySession_IdOrderByStudent_CollegeIdentifierAsc(Long sessionId);
}

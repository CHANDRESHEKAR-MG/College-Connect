package com.collegeconnect.repository;

import com.collegeconnect.entity.AbsenceCase;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AbsenceCaseRepository extends JpaRepository<AbsenceCase, Long> {

    Optional<AbsenceCase> findByAttendance_Id(Long attendanceId);

    List<AbsenceCase> findByAttendance_Student_IdOrderByCreatedAtDesc(Long studentId);

    List<AbsenceCase> findAllByOrderByCreatedAtDesc();

    @Query("select ac from AbsenceCase ac "
            + "join ac.attendance attendance join attendance.session session "
            + "left join session.substituteProfessor substitute "
            + "where lower(session.assignment.status) = 'active' "
            + "and (session.assignment.validFrom is null "
            + "or session.assignment.validFrom <= session.sessionDate) "
            + "and (session.assignment.validTo is null "
            + "or session.assignment.validTo >= session.sessionDate) "
            + "and (session.assignment.professor.account.id = :accountId "
            + "or substitute.account.id = :accountId) "
            + "order by ac.createdAt desc")
    List<AbsenceCase> findAssignedToProfessor(@Param("accountId") Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ac from AbsenceCase ac where ac.id = :caseId")
    Optional<AbsenceCase> findByIdForUpdate(@Param("caseId") Long caseId);
}

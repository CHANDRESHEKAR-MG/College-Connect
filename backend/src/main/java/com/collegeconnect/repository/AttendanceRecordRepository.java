package com.collegeconnect.repository;

import com.collegeconnect.entity.AttendanceRecord;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ar from AttendanceRecord ar where ar.id = :attendanceId")
    Optional<AttendanceRecord> findByIdForUpdate(@Param("attendanceId") Long attendanceId);

    List<AttendanceRecord> findByStudent_Id(Long studentId);

    @Query("select ar from AttendanceRecord ar "
            + "join fetch ar.session session "
            + "join fetch session.assignment assignment "
            + "join fetch assignment.offering offering "
            + "join fetch offering.subject subj "
            + "join fetch offering.section sec "
            + "join fetch ar.student student "
            + "left join fetch session.substituteProfessor substitute "
            + "where session.sessionDate between :startDate and :endDate "
            + "and (:studentId is null or student.id = :studentId) "
            + "and (:sectionId is null or sec.id = :sectionId) "
            + "and (:offeringId is null or offering.id = :offeringId) "
            + "and (:professorAccountId is null or (lower(assignment.status) = 'active' "
            + "and (assignment.validFrom is null or assignment.validFrom <= session.sessionDate) "
            + "and (assignment.validTo is null or assignment.validTo >= session.sessionDate) "
            + "and (assignment.professor.account.id = :professorAccountId "
            + "or substitute.account.id = :professorAccountId))) "
            + "order by session.sessionDate, sec.name, subj.code, student.collegeIdentifier")
    List<AttendanceRecord> findDateRangeReport(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("studentId") Long studentId,
            @Param("sectionId") Long sectionId,
            @Param("offeringId") Long offeringId,
            @Param("professorAccountId") Long professorAccountId,
            Pageable pageable);
}

package com.collegeconnect.repository;

import com.collegeconnect.entity.ClassSession;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cs from ClassSession cs where cs.id = :sessionId")
    Optional<ClassSession> findByIdForUpdate(@Param("sessionId") Long sessionId);

    @Query("select (count(cs) > 0) from ClassSession cs left join cs.substituteProfessor substitute "
            + "where cs.id = :sessionId "
            + "and lower(cs.assignment.status) = 'active' "
            + "and (cs.assignment.validFrom is null or cs.assignment.validFrom <= cs.sessionDate) "
            + "and (cs.assignment.validTo is null or cs.assignment.validTo >= cs.sessionDate) "
            + "and (cs.assignment.professor.account.id = :accountId "
            + "or substitute.account.id = :accountId)")
    boolean isAssignedProfessorForSession(
            @Param("sessionId") Long sessionId, @Param("accountId") Long accountId);

    @Query("select (count(cs) > 0) from ClassSession cs left join cs.substituteProfessor substitute "
            + "where cs.assignment.offering.section.id = :sectionId "
            + "and cs.sessionDate between :startDate and :endDate "
            + "and lower(cs.assignment.status) = 'active' "
            + "and (cs.assignment.validFrom is null or cs.assignment.validFrom <= cs.sessionDate) "
            + "and (cs.assignment.validTo is null or cs.assignment.validTo >= cs.sessionDate) "
            + "and (cs.assignment.professor.account.id = :accountId "
            + "or substitute.account.id = :accountId)")
    boolean hasAssignedSessionForSectionAndDateRange(
            @Param("accountId") Long accountId,
            @Param("sectionId") Long sectionId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("select (count(cs) > 0) from ClassSession cs left join cs.substituteProfessor substitute "
            + "where cs.assignment.offering.id = :offeringId "
            + "and cs.sessionDate between :startDate and :endDate "
            + "and lower(cs.assignment.status) = 'active' "
            + "and (cs.assignment.validFrom is null or cs.assignment.validFrom <= cs.sessionDate) "
            + "and (cs.assignment.validTo is null or cs.assignment.validTo >= cs.sessionDate) "
            + "and (cs.assignment.professor.account.id = :accountId "
            + "or substitute.account.id = :accountId)")
    boolean hasAssignedSessionForOfferingAndDateRange(
            @Param("accountId") Long accountId,
            @Param("offeringId") Long offeringId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("select (count(cs) > 0) from ClassSession cs "
            + "where cs.assignment.offering.section.id = :sectionId "
            + "and cs.sessionDate between :startDate and :endDate "
            + "and lower(cs.status) <> 'cancelled' "
            + "and (cs.sessionDate < :today "
            + "or (cs.sessionDate = :today and cs.startTime <= :currentTime))")
    boolean hasStartedSessionInSectionAndDateRange(
            @Param("sectionId") Long sectionId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("today") LocalDate today,
            @Param("currentTime") LocalTime currentTime);
}

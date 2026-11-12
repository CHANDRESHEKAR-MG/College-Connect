package com.collegeconnect.repository;

import com.collegeconnect.entity.LeaveRequest;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByRequester_IdOrderBySubmittedAtDesc(Long requesterId);

    List<LeaveRequest> findAllByOrderBySubmittedAtDesc();

    @Query("select distinct lr from LeaveRequest lr "
            + "where exists (select cs.id from ClassSession cs left join cs.substituteProfessor substitute "
            + "where cs.assignment.offering.section.id = lr.student.section.id "
            + "and cs.sessionDate between lr.startDate and lr.endDate "
            + "and lower(cs.assignment.status) = 'active' "
            + "and (cs.assignment.validFrom is null or cs.assignment.validFrom <= cs.sessionDate) "
            + "and (cs.assignment.validTo is null or cs.assignment.validTo >= cs.sessionDate) "
            + "and (cs.assignment.professor.account.id = :accountId "
            + "or substitute.account.id = :accountId)) "
            + "order by lr.submittedAt desc")
    List<LeaveRequest> findAssignedToProfessor(@Param("accountId") Long accountId);

    List<LeaveRequest> findByStudent_IdAndStatusIn(Long studentId, List<String> statuses);

    Optional<LeaveRequest> findByIdAndRequester_Id(Long id, Long requesterId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lr from LeaveRequest lr where lr.id = :requestId")
    Optional<LeaveRequest> findByIdForUpdate(@Param("requestId") Long requestId);

    @Query("select lr from LeaveRequest lr where lr.student.id in :studentIds "
            + "and lr.status = 'APPROVED' and lr.startDate <= :sessionDate and lr.endDate >= :sessionDate")
    List<LeaveRequest> findApprovedForStudentsOnDate(
            @Param("studentIds") List<Long> studentIds,
            @Param("sessionDate") LocalDate sessionDate);
}

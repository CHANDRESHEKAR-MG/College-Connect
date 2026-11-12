package com.collegeconnect.repository;

import com.collegeconnect.entity.SupportingDocument;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupportingDocumentRepository extends JpaRepository<SupportingDocument, Long> {

    List<SupportingDocument> findByAbsenceCase_IdOrderByUploadedAtAsc(Long absenceCaseId);

    List<SupportingDocument> findByLeaveRequest_IdOrderByUploadedAtAsc(Long leaveRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from SupportingDocument d where d.id = :documentId")
    java.util.Optional<SupportingDocument> findByIdForUpdate(@Param("documentId") Long documentId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select d from SupportingDocument d where d.id = :documentId")
    java.util.Optional<SupportingDocument> findByIdForRead(@Param("documentId") Long documentId);
}

package com.collegeconnect.repository;

import com.collegeconnect.entity.NotificationOutbox;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.time.Instant;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    long countByStatus(String status);

    List<NotificationOutbox> findByStatusInOrderByCreatedAtAsc(List<String> statuses, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from NotificationOutbox event where event.id = :eventId")
    Optional<NotificationOutbox> findByIdForUpdate(@Param("eventId") Long eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from NotificationOutbox event "
            + "where event.status = 'PENDING' "
            + "or (event.status = 'FAILED_RETRYABLE' "
            + "and (event.nextAttemptAt is null or event.nextAttemptAt <= :now)) "
            + "order by event.createdAt asc")
    List<NotificationOutbox> findNextAvailableForUpdate(@Param("now") Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from NotificationOutbox event "
            + "where event.status = 'PROCESSING' and event.leaseUntil < :leaseExpiry")
    List<NotificationOutbox> findExpiredProcessingForUpdate(
            @Param("leaseExpiry") Instant leaseExpiry, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from NotificationOutbox event "
            + "where event.sourceType = :sourceType and event.sourceId = :sourceId")
    List<NotificationOutbox> findBySourceTypeAndSourceIdForUpdate(
            @Param("sourceType") String sourceType,
            @Param("sourceId") Long sourceId);
}

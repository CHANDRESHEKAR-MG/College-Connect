package com.collegeconnect.repository;

import com.collegeconnect.entity.NotificationAttempt;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationAttemptRepository extends JpaRepository<NotificationAttempt, Long> {

    Optional<NotificationAttempt> findByEvent_IdAndAttemptNumber(Long eventId, int attemptNumber);

    Optional<NotificationAttempt> findByProviderMessageId(String providerMessageId);

    Optional<NotificationAttempt> findByRequestCorrelationId(String requestCorrelationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from NotificationAttempt attempt "
            + "where attempt.providerMessageId = :providerMessageId")
    Optional<NotificationAttempt> findByProviderMessageIdForUpdate(
            @Param("providerMessageId") String providerMessageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from NotificationAttempt attempt "
            + "where attempt.requestCorrelationId = :correlationId")
    Optional<NotificationAttempt> findByRequestCorrelationIdForUpdate(
            @Param("correlationId") String correlationId);

    boolean existsByEvent_IdAndProviderMessageIdIsNotNull(Long eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from NotificationAttempt attempt where attempt.id = :attemptId")
    Optional<NotificationAttempt> findByIdForUpdate(@Param("attemptId") Long attemptId);
}

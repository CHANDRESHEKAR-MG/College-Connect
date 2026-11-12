package com.collegeconnect.repository;

import com.collegeconnect.entity.WhatsAppWebhookEvent;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface WhatsAppWebhookEventRepository extends JpaRepository<WhatsAppWebhookEvent, Long> {

    boolean existsByProviderEventId(String providerEventId);

    List<WhatsAppWebhookEvent> findByProcessingStatusOrderByReceivedAtAsc(
            String processingStatus, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from WhatsAppWebhookEvent event "
            + "where event.processingStatus = 'RECEIVED' order by event.receivedAt asc")
    List<WhatsAppWebhookEvent> findNextReceivedForUpdate(Pageable pageable);
}

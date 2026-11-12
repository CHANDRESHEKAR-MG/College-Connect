package com.collegeconnect.service;

import com.collegeconnect.dto.WhatsAppWebhookEventResponse;
import com.collegeconnect.repository.WhatsAppWebhookEventRepository;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WhatsAppWebhookMonitoringService {

    private static final Set<String> VISIBLE_STATUSES =
            Set.of("RECONCILIATION_REQUIRED", "PROCESSING_FAILED");

    private final WhatsAppWebhookEventRepository repository;

    public WhatsAppWebhookMonitoringService(WhatsAppWebhookEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<WhatsAppWebhookEventResponse> list(String status, int limit) {
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be between 1 and 100");
        }
        String selectedStatus =
                status == null ? "RECONCILIATION_REQUIRED" : status.toUpperCase(Locale.ROOT);
        if (!VISIBLE_STATUSES.contains(selectedStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown webhook processing status");
        }
        return repository.findByProcessingStatusOrderByReceivedAtAsc(selectedStatus, PageRequest.of(0, limit))
                .stream()
                .map(event -> new WhatsAppWebhookEventResponse(
                        event.getId(),
                        event.getEventType(),
                        event.getProcessingStatus(),
                        event.getPayloadHash(),
                        event.getParent() == null ? null : event.getParent().getId(),
                        event.getAttempt() == null ? null : event.getAttempt().getId(),
                        event.getProcessingError(),
                        event.getReceivedAt()))
                .toList();
    }
}

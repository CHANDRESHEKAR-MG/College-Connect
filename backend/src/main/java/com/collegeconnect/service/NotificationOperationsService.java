package com.collegeconnect.service;

import com.collegeconnect.dto.NotificationOutboxActionRequest;
import com.collegeconnect.dto.NotificationOutboxResponse;
import com.collegeconnect.dto.NotificationOutboxSummaryResponse;
import com.collegeconnect.dto.NotificationReconciliationRequest;
import com.collegeconnect.dto.NotificationResendRequest;
import com.collegeconnect.entity.AuditLog;
import com.collegeconnect.entity.NotificationAttempt;
import com.collegeconnect.entity.NotificationOutbox;
import com.collegeconnect.entity.UserAccount;
import com.collegeconnect.repository.AuditLogRepository;
import com.collegeconnect.repository.NotificationAttemptRepository;
import com.collegeconnect.repository.NotificationOutboxRepository;
import com.collegeconnect.repository.UserAccountRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationOperationsService {

    private static final List<String> STATES = List.of(
            "PENDING",
            "PROCESSING",
            "SENT",
            "DELIVERED",
            "READ",
            "DELIVERY_FAILED",
            "FAILED_RETRYABLE",
            "FAILED_DEAD_LETTER",
            "AMBIGUOUS_DELIVERY",
            "CANCELLED",
            "SKIPPED_PREFERENCE",
            "SKIPPED_NO_OPTIN");

    private final NotificationOutboxRepository notificationOutboxRepository;
    private final NotificationAttemptRepository notificationAttemptRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuditLogRepository auditLogRepository;

    public NotificationOperationsService(
            NotificationOutboxRepository notificationOutboxRepository,
            NotificationAttemptRepository notificationAttemptRepository,
            UserAccountRepository userAccountRepository,
            AuditLogRepository auditLogRepository) {
        this.notificationOutboxRepository = notificationOutboxRepository;
        this.notificationAttemptRepository = notificationAttemptRepository;
        this.userAccountRepository = userAccountRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public NotificationOutboxSummaryResponse summary() {
        Map<String, Long> counts = new LinkedHashMap<>();
        STATES.forEach(state -> counts.put(state, notificationOutboxRepository.countByStatus(state)));
        return new NotificationOutboxSummaryResponse(Map.copyOf(counts));
    }

    @Transactional(readOnly = true)
    public List<NotificationOutboxResponse> list(String status, int limit) {
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be between 1 and 100");
        }
        List<String> statuses = status == null
                ? STATES
                : STATES.stream().filter(state -> state.equalsIgnoreCase(status)).toList();
        if (statuses.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown outbox status");
        }
        return notificationOutboxRepository
                .findByStatusInOrderByCreatedAtAsc(statuses, PageRequest.of(0, limit))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NotificationOutboxResponse retryConfirmed(
            Long adminAccountId, Long eventId, NotificationOutboxActionRequest request) {
        NotificationOutbox event = notificationOutboxRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Outbox event not found"));
        if (!"FAILED_RETRYABLE".equals(event.getStatus())
                && !"FAILED_DEAD_LETTER".equals(event.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only failed events may be retried");
        }
        if (!request.confirmedNeverAccepted()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Retry requires confirmation that the provider never accepted the event");
        }
        if (notificationAttemptRepository.existsByEvent_IdAndProviderMessageIdIsNotNull(eventId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "An event with a provider message id cannot be retried");
        }
        event.queueConfirmedRetry(Instant.now());
        audit(adminAccountId, "OUTBOX_RETRY", eventId, request.reason());
        return toResponse(event);
    }

    @Transactional
    public NotificationOutboxResponse resend(
            Long adminAccountId, Long eventId, NotificationResendRequest request) {
        NotificationOutbox original = notificationOutboxRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Outbox event not found"));
        if (!"SENT".equals(original.getStatus())
                && !"DELIVERED".equals(original.getStatus())
                && !"READ".equals(original.getStatus())
                && !"DELIVERY_FAILED".equals(original.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Explicit resend is allowed only after provider acceptance is known");
        }
        if (original.getParent() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Skipped events do not have a resend recipient");
        }
        Instant createdAt = Instant.now();
        NotificationOutbox resend = new NotificationOutbox(
                original.getStudent(),
                original.getParent(),
                original.getSourceType(),
                original.getSourceId(),
                original.getTemplateName(),
                original.getPayloadJson(),
                "resend:" + eventId + ":" + UUID.randomUUID(),
                "PENDING",
                createdAt);
        resend.linkToOriginal(original);
        resend = notificationOutboxRepository.save(resend);
        audit(adminAccountId, "OUTBOX_RESEND", resend.getId(), request.reason());
        return toResponse(resend);
    }

    @Transactional
    public NotificationOutboxResponse reconcileAmbiguous(
            Long adminAccountId, Long eventId, NotificationReconciliationRequest request) {
        NotificationOutbox event = notificationOutboxRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Outbox event not found"));
        if (!"AMBIGUOUS_DELIVERY".equals(event.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Event is not awaiting reconciliation");
        }
        Instant now = Instant.now();
        if ("CONFIRMED_NEVER_ACCEPTED".equals(request.outcome())) {
            if (notificationAttemptRepository.existsByEvent_IdAndProviderMessageIdIsNotNull(eventId)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "A stored provider message id contradicts this resolution");
            }
            event.queueConfirmedRetry(now);
            audit(adminAccountId, "OUTBOX_RECONCILE_NEVER_ACCEPTED", eventId, request.reason());
            return toResponse(event);
        }
        if (request.providerMessageId() == null || request.providerMessageId().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Provider message id is required for accepted delivery");
        }
        NotificationAttempt attempt = notificationAttemptRepository
                .findByEvent_IdAndAttemptNumber(eventId, event.getAttemptCount())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "No attempt is available for this event"));
        attempt.accepted(request.providerMessageId().trim(), null, now);
        event.markSent(now);
        audit(adminAccountId, "OUTBOX_RECONCILE_ACCEPTED", eventId, request.reason());
        return toResponse(event);
    }

    private void audit(Long adminAccountId, String action, Long eventId, String reason) {
        UserAccount admin = userAccountRepository.findById(adminAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        auditLogRepository.save(new AuditLog(
                admin,
                "ADMIN",
                action,
                "NOTIFICATION_OUTBOX",
                eventId.toString(),
                reason.trim(),
                Instant.now()));
    }

    private NotificationOutboxResponse toResponse(NotificationOutbox event) {
        return new NotificationOutboxResponse(
                event.getId(),
                event.getStudent().getId(),
                event.getParent() == null ? null : event.getParent().getId(),
                event.getSourceType(),
                event.getSourceId(),
                event.getTemplateName(),
                event.getStatus(),
                event.getCreatedAt());
    }
}

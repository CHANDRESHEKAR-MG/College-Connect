package com.collegeconnect.notification;

import com.collegeconnect.entity.NotificationAttempt;
import com.collegeconnect.entity.NotificationDefaultPreference;
import com.collegeconnect.entity.NotificationOutbox;
import com.collegeconnect.entity.NotificationPreference;
import com.collegeconnect.entity.ParentProfile;
import com.collegeconnect.repository.NotificationAttemptRepository;
import com.collegeconnect.repository.NotificationDefaultPreferenceRepository;
import com.collegeconnect.repository.NotificationOutboxRepository;
import com.collegeconnect.repository.NotificationPreferenceRepository;
import com.collegeconnect.repository.ParentStudentLinkRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationDeliveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDeliveryService.class);

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationAttemptRepository attemptRepository;
    private final ParentStudentLinkRepository parentStudentLinkRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationDefaultPreferenceRepository defaultPreferenceRepository;
    private final WhatsAppProperties properties;

    public NotificationDeliveryService(
            NotificationOutboxRepository outboxRepository,
            NotificationAttemptRepository attemptRepository,
            ParentStudentLinkRepository parentStudentLinkRepository,
            NotificationPreferenceRepository preferenceRepository,
            NotificationDefaultPreferenceRepository defaultPreferenceRepository,
            WhatsAppProperties properties) {
        this.outboxRepository = outboxRepository;
        this.attemptRepository = attemptRepository;
        this.parentStudentLinkRepository = parentStudentLinkRepository;
        this.preferenceRepository = preferenceRepository;
        this.defaultPreferenceRepository = defaultPreferenceRepository;
        this.properties = properties;
    }

    @Transactional
    public Optional<NotificationDeliveryClaim> claimNext() {
        Instant now = Instant.now();
        List<NotificationOutbox> events =
                outboxRepository.findNextAvailableForUpdate(now, PageRequest.of(0, 1));
        if (events.isEmpty()) {
            return Optional.empty();
        }
        NotificationOutbox event = events.get(0);
        ParentProfile parent = event.getParent();
        if (parent == null || !parent.hasVerifiedMobile() || !parent.isWhatsappOptIn()
                || !"ACTIVE".equalsIgnoreCase(parent.getAccount().getStatus())
                || parentStudentLinkRepository
                        .findByParent_IdAndStudent_Id(parent.getId(), event.getStudent().getId()).stream()
                        .noneMatch(link -> link.isActiveVerified())) {
            event.skip("SKIPPED_NO_OPTIN", now);
            return Optional.empty();
        }

        String category = preferenceCategory(event);
        if (category == null) {
            LOGGER.error("Outbox event {} uses an unsupported WhatsApp template", event.getId());
            event.markDeadLetter(now);
            return Optional.empty();
        }
        if (!isCategoryEnabled(parent.getId(), category)) {
            event.skip("SKIPPED_PREFERENCE", now);
            return Optional.empty();
        }

        int attemptNumber = event.getAttemptCount() + 1;
        String correlationId = UUID.randomUUID().toString();
        event.claim(attemptNumber, now.plus(properties.getLeaseDuration()), now);
        attemptRepository.save(new NotificationAttempt(
                event, attemptNumber, correlationId, "PROCESSING", now));
        return Optional.of(new NotificationDeliveryClaim(
                event.getId(),
                attemptNumber,
                parent.getVerifiedMobile(),
                event.getTemplateName(),
                event.getPayloadJson(),
                correlationId));
    }

    @Transactional
    public void recordAccepted(NotificationDeliveryClaim claim, WhatsAppProvider.SendResult result) {
        if (result == null || result.providerMessageId() == null || result.providerMessageId().isBlank()) {
            throw new IllegalStateException("WhatsApp provider acceptance requires a provider message id");
        }
        NotificationOutbox event = requireLockedEvent(claim.eventId());
        NotificationAttempt attempt = requireAttempt(claim);
        Instant now = Instant.now();
        attempt.accepted(result.providerMessageId(), result.httpStatus(), now);
        if (!"DELIVERED".equals(event.getStatus())
                && !"READ".equals(event.getStatus())
                && !"DELIVERY_FAILED".equals(event.getStatus())) {
            event.markSent(now);
        }
    }

    @Transactional
    public void recordFailure(NotificationDeliveryClaim claim, WhatsAppDeliveryException failure) {
        NotificationOutbox event = requireLockedEvent(claim.eventId());
        NotificationAttempt attempt = requireAttempt(claim);
        if (attempt.getProviderMessageId() != null || "SENT".equals(event.getStatus())
                || "DELIVERED".equals(event.getStatus())
                || "READ".equals(event.getStatus())
                || "DELIVERY_FAILED".equals(event.getStatus())) {
            return;
        }

        Instant now = Instant.now();
        switch (failure.getOutcome()) {
            case AMBIGUOUS -> {
                attempt.failed(
                        "UNKNOWN",
                        failure.getProviderCode(),
                        failure.getHttpStatus(),
                        failure.getMessage(),
                        now);
                event.markAmbiguous(now);
            }
            case PERMANENT -> {
                attempt.failed(
                        "REJECTED",
                        failure.getProviderCode(),
                        failure.getHttpStatus(),
                        failure.getMessage(),
                        now);
                event.markDeadLetter(now);
            }
            case RETRYABLE -> {
                attempt.failed(
                        "REJECTED",
                        failure.getProviderCode(),
                        failure.getHttpStatus(),
                        failure.getMessage(),
                        now);
                if (claim.attemptNumber() >= properties.getMaxAttempts()) {
                    event.markDeadLetter(now);
                } else {
                    event.markRetryable(now.plus(backoff(claim.attemptNumber())), now);
                }
            }
        }
    }

    @Transactional
    public int recoverExpiredLeases() {
        Instant now = Instant.now();
        List<NotificationOutbox> staleEvents =
                outboxRepository.findExpiredProcessingForUpdate(now, PageRequest.of(0, 100));
        for (NotificationOutbox event : staleEvents) {
            Optional<NotificationAttempt> attempt = attemptRepository.findByEvent_IdAndAttemptNumber(
                    event.getId(), event.getAttemptCount());
            if (attempt.isPresent()) {
                if (attempt.get().getProviderMessageId() != null) {
                    event.markSent(now);
                    continue;
                }
                attempt.get().failed(
                            "UNKNOWN",
                            "WORKER_LEASE_EXPIRED",
                            null,
                            "Worker lease expired before the attempt outcome was recorded",
                            now);
            }
            event.markAmbiguous(now);
        }
        return staleEvents.size();
    }

    private NotificationOutbox requireLockedEvent(Long eventId) {
        return outboxRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Outbox event not found"));
    }

    private NotificationAttempt requireAttempt(NotificationDeliveryClaim claim) {
        return attemptRepository
                .findByEvent_IdAndAttemptNumber(claim.eventId(), claim.attemptNumber())
                .orElseThrow(() -> new IllegalStateException("Notification attempt was not found"));
    }

    private Duration backoff(int attemptNumber) {
        long delay = Math.max(1, properties.getInitialBackoff().toMillis());
        long maximum = Math.max(delay, properties.getMaxBackoff().toMillis());
        for (int attempt = 1; attempt < attemptNumber && delay < maximum; attempt++) {
            delay = delay > maximum / 2 ? maximum : delay * 2;
        }
        return Duration.ofMillis(Math.min(delay, maximum));
    }

    private String preferenceCategory(NotificationOutbox event) {
        return switch (event.getTemplateName().toUpperCase(Locale.ROOT)) {
            case "TPL-ATT-ABSENT" -> "ATTENDANCE_ABSENT";
            case "TPL-ATT-PRESENT" -> "ATTENDANCE_PRESENT";
            case "TPL-ATT-SUMMARY" -> "ATTENDANCE_SUMMARY";
            case "TPL-ABS-REMIND" -> "ABSENCE_REMINDER";
            case "TPL-ABS-DECISION" -> "ABSENCE";
            case "TPL-LVE-SUBMITTED" -> "LEAVE_SUBMITTED";
            case "TPL-LVE-DECISION" -> "LEAVE";
            case "TPL-CIE-PUB" -> "CIE";
            case "TPL-SEE-PUB" -> "SEE";
            case "TPL-COR" -> event.getPayloadJson().contains("\"status\":\"PRESENT\"")
                    ? "ATTENDANCE_PRESENT"
                    : "ATTENDANCE_ABSENT";
            default -> null;
        };
    }

    private boolean isCategoryEnabled(Long parentId, String category) {
        NotificationPreference allMessages = preferenceRepository
                .findByParent_IdAndCategory(parentId, "ALL_MESSAGES")
                .orElse(null);
        NotificationDefaultPreference globalAllMessages =
                defaultPreferenceRepository.findByCategory("ALL_MESSAGES").orElse(null);
        if ((allMessages != null && !allMessages.isEnabled())
                || (allMessages == null && (globalAllMessages == null || !globalAllMessages.isEnabled()))) {
            return false;
        }
        NotificationPreference parentPreference = preferenceRepository
                .findByParent_IdAndCategory(parentId, category)
                .orElse(null);
        if (parentPreference != null) {
            return parentPreference.isEnabled();
        }
        NotificationDefaultPreference global = defaultPreferenceRepository.findByCategory(category)
                .orElse(null);
        return global != null && global.isEnabled();
    }
}

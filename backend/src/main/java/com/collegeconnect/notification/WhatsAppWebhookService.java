package com.collegeconnect.notification;

import com.collegeconnect.entity.NotificationAttempt;
import com.collegeconnect.entity.NotificationOutbox;
import com.collegeconnect.entity.ParentProfile;
import com.collegeconnect.entity.ParentStudentLink;
import com.collegeconnect.entity.WhatsAppConsentEvent;
import com.collegeconnect.entity.WhatsAppWebhookEvent;
import com.collegeconnect.repository.NotificationAttemptRepository;
import com.collegeconnect.repository.NotificationOutboxRepository;
import com.collegeconnect.repository.ParentProfileRepository;
import com.collegeconnect.repository.ParentStudentLinkRepository;
import com.collegeconnect.repository.WhatsAppConsentEventRepository;
import com.collegeconnect.repository.WhatsAppWebhookEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WhatsAppWebhookService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WhatsAppWebhookService.class);

    private final WhatsAppProperties properties;
    private final WhatsAppWebhookEventRepository webhookRepository;
    private final NotificationAttemptRepository attemptRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final ParentProfileRepository parentProfileRepository;
    private final ParentStudentLinkRepository parentStudentLinkRepository;
    private final WhatsAppConsentEventRepository consentEventRepository;
    private final ObjectMapper objectMapper;

    public WhatsAppWebhookService(
            WhatsAppProperties properties,
            WhatsAppWebhookEventRepository webhookRepository,
            NotificationAttemptRepository attemptRepository,
            NotificationOutboxRepository outboxRepository,
            ParentProfileRepository parentProfileRepository,
            ParentStudentLinkRepository parentStudentLinkRepository,
            WhatsAppConsentEventRepository consentEventRepository,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.webhookRepository = webhookRepository;
        this.attemptRepository = attemptRepository;
        this.outboxRepository = outboxRepository;
        this.parentProfileRepository = parentProfileRepository;
        this.parentStudentLinkRepository = parentStudentLinkRepository;
        this.consentEventRepository = consentEventRepository;
        this.objectMapper = objectMapper;
    }

    public boolean verifySignature(String payload, String signature) {
        if (properties.getAppSecret().isBlank() || signature == null || !signature.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.getAppSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)))
                    .getBytes(StandardCharsets.US_ASCII);
            byte[] provided = signature.substring("sha256=".length()).toLowerCase(Locale.ROOT)
                    .getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(expected, provided);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to verify WhatsApp webhook signature", exception);
        }
    }

    public boolean verifyChallenge(String mode, String token) {
        if (!"subscribe".equals(mode) || properties.getVerifyToken().isBlank() || token == null) {
            return false;
        }
        return MessageDigest.isEqual(
                properties.getVerifyToken().getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    @Transactional(readOnly = true)
    public boolean isDuplicateDelivery(String payload) {
        List<String> keys = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(payload);
            for (JsonNode value : values(root, "statuses")) {
                String messageId = value.path("id").asText("");
                if (!messageId.isBlank()) {
                    keys.add("status:" + messageId + ":"
                            + value.path("status").asText("") + ":"
                            + value.path("timestamp").asText(""));
                }
            }
            for (JsonNode value : values(root, "messages")) {
                String messageId = value.path("id").asText("");
                if (!messageId.isBlank()) {
                    keys.add("inbound:" + messageId);
                }
            }
        } catch (JsonProcessingException exception) {
            keys.clear();
        }
        if (keys.isEmpty()) {
            keys.add("payload:" + sha256(payload));
        }
        return keys.stream().allMatch(key -> webhookRepository.existsByProviderEventId(sha256(key)));
    }

    @Transactional
    public void storeWebhook(String payload) {
        if (payload.length() > 12000) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Webhook payload is too large");
        }
        String payloadHash = sha256(payload);
        List<WhatsAppWebhookEvent> events = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(payload);
            for (JsonNode value : values(root, "statuses")) {
                String messageId = value.path("id").asText("");
                String status = value.path("status").asText("");
                String timestamp = value.path("timestamp").asText("");
                if (!messageId.isBlank()) {
                    events.add(newWebhookEvent(
                            "STATUS",
                            "status:" + messageId + ":" + status + ":" + timestamp,
                            payloadHash,
                            value.toString()));
                }
            }
            for (JsonNode value : values(root, "messages")) {
                String messageId = value.path("id").asText("");
                if (!messageId.isBlank()) {
                    events.add(newWebhookEvent(
                            "INBOUND", "inbound:" + messageId, payloadHash, value.toString()));
                }
            }
            if (events.isEmpty()) {
                events.add(newWebhookEvent("WEBHOOK", "payload:" + payloadHash, payloadHash, payload));
            }
        } catch (JsonProcessingException exception) {
            events.add(newWebhookEvent("INVALID", "payload:" + payloadHash, payloadHash, payload));
        }
        for (WhatsAppWebhookEvent event : events) {
            if (!webhookRepository.existsByProviderEventId(event.getProviderEventId())) {
                webhookRepository.save(event);
            }
        }
        webhookRepository.flush();
    }

    @Transactional
    public boolean processNextWebhook() {
        List<WhatsAppWebhookEvent> events =
                webhookRepository.findNextReceivedForUpdate(PageRequest.of(0, 1));
        if (events.isEmpty()) {
            return false;
        }
        WhatsAppWebhookEvent event = events.get(0);
        event.markProcessingStarted();
        try {
            JsonNode payload = objectMapper.readTree(event.getPayloadJson());
            if ("STATUS".equals(event.getEventType())) {
                if (!applyStatus(payload, event)) {
                    event.markReconciliationRequired(
                            "No matching notification attempt or provider correlation was found", Instant.now());
                    return true;
                }
            } else if ("INBOUND".equals(event.getEventType())) {
                processInbound(payload, event);
            } else if ("INVALID".equals(event.getEventType())) {
                throw new IllegalArgumentException("Webhook payload is not valid JSON");
            }
            event.markProcessed(Instant.now());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            event.markProcessingFailure(exception.getMessage(), Instant.now());
            LOGGER.warn("WhatsApp webhook event {} could not be processed", event.getProviderEventId());
        }
        return true;
    }

    private WhatsAppWebhookEvent newWebhookEvent(
            String type, String key, String payloadHash, String payload) {
        return new WhatsAppWebhookEvent(sha256(key), type, payloadHash, payload, Instant.now());
    }

    private boolean applyStatus(JsonNode status, WhatsAppWebhookEvent webhookEvent) {
        String providerMessageId = status.path("id").asText("");
        String correlationId = status.path("biz_opaque_callback_data").asText("");
        NotificationAttempt candidate = providerMessageId.isBlank()
                ? null
                : attemptRepository.findByProviderMessageId(providerMessageId).orElse(null);
        if (candidate == null && !correlationId.isBlank()) {
            candidate = attemptRepository.findByRequestCorrelationId(correlationId).orElse(null);
        }
        if (candidate == null || providerMessageId.isBlank()) {
            return false;
        }
        outboxRepository.findByIdForUpdate(candidate.getEvent().getId())
                .orElseThrow(() -> new IllegalStateException("Outbox event for callback attempt not found"));
        NotificationAttempt attempt = attemptRepository.findByIdForUpdate(candidate.getId())
                .orElseThrow(() -> new IllegalStateException("Callback attempt not found"));
        webhookEvent.attachAttempt(attempt);
        if (attempt.getProviderMessageId() != null
                && !attempt.getProviderMessageId().equals(providerMessageId)) {
            return false;
        }

        Instant now = Instant.now();
        if (attempt.getProviderMessageId() == null) {
            attempt.accepted(providerMessageId, null, now);
        }
        String nextStatus = status.path("status").asText("").toLowerCase(Locale.ROOT);
        String currentStatus = attempt.getDeliveryStatus();
        if (deliveryRank(nextStatus) < 0
                || deliveryRank(nextStatus) < deliveryRank(currentStatus)
                || ("FAILED".equalsIgnoreCase(currentStatus) && "sent".equals(nextStatus))) {
            return true;
        }
        String error = status.path("errors").isArray() && !status.path("errors").isEmpty()
                ? status.path("errors").get(0).path("title").asText("Provider delivery failed")
                : null;
        attempt.updateDeliveryStatus(nextStatus.toUpperCase(Locale.ROOT), null, error, now);

        NotificationOutbox event = attempt.getEvent();
        switch (nextStatus) {
            case "sent" -> {
                if (!"DELIVERED".equals(event.getStatus()) && !"READ".equals(event.getStatus())) {
                    event.markSent(now);
                }
            }
            case "delivered" -> event.markDelivered(now);
            case "read" -> event.markRead(now);
            case "failed" -> {
                if (!"DELIVERED".equals(event.getStatus()) && !"READ".equals(event.getStatus())) {
                    event.markDeliveryFailed(now);
                }
            }
            default -> {
                return true;
            }
        }
        return true;
    }

    private void processInbound(JsonNode message, WhatsAppWebhookEvent webhookEvent) {
        String number = message.path("from").asText("");
        String normalizedNumber = number.replaceAll("\\D", "");
        if (normalizedNumber.isBlank()) {
            return;
        }
        ParentProfile parent = parentProfileRepository.findByVerifiedMobile(normalizedNumber)
                .or(() -> parentProfileRepository.findByVerifiedMobile("+" + normalizedNumber))
                .orElse(null);
        if (parent == null || !parent.hasVerifiedMobile()
                || !"ACTIVE".equalsIgnoreCase(parent.getAccount().getStatus())) {
            return;
        }
        webhookEvent.attachParent(parent);
        String text = message.path("text").path("body").asText("");
        if (text.isBlank()) {
            return;
        }
        String command = text.trim().toUpperCase(Locale.ROOT);
        Instant now = Instant.now();
        if ("STOP".equals(command) && parent.isWhatsappOptIn()) {
            parent.recordWhatsappConsent(false, now);
            parentProfileRepository.save(parent);
            consentEventRepository.save(new WhatsAppConsentEvent(
                    parent, null, null, "OPT_OUT", "WHATSAPP_KEYWORD", now));
        } else if ("START".equals(command) && !parent.isWhatsappOptIn()) {
            List<ParentStudentLink> links = parentStudentLinkRepository.findByParent_Id(parent.getId());
            if (links.stream().noneMatch(ParentStudentLink::isActiveVerified)) {
                return;
            }
            parent.recordWhatsappConsent(true, now);
            parentProfileRepository.save(parent);
            consentEventRepository.save(new WhatsAppConsentEvent(
                    parent, null, null, "OPT_IN", "WHATSAPP_KEYWORD", now));
        }
    }

    private List<JsonNode> values(JsonNode root, String fieldName) {
        List<JsonNode> values = new ArrayList<>();
        JsonNode entries = root.path("entry");
        if (!entries.isArray()) {
            return values;
        }
        for (JsonNode entry : entries) {
            JsonNode changes = entry.path("changes");
            if (!changes.isArray()) {
                continue;
            }
            for (JsonNode change : changes) {
                JsonNode fieldValues = change.path("value").path(fieldName);
                if (fieldValues.isArray()) {
                    fieldValues.forEach(values::add);
                }
            }
        }
        return values;
    }

    private int deliveryRank(String status) {
        if (status == null) {
            return -1;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "failed" -> 1;
            case "sent" -> 1;
            case "delivered" -> 2;
            case "read" -> 3;
            default -> -1;
        };
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

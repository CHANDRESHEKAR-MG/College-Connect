package com.collegeconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.collegeconnect.entity.NotificationAttempt;
import com.collegeconnect.entity.NotificationOutbox;
import com.collegeconnect.entity.ParentProfile;
import com.collegeconnect.entity.ParentStudentLink;
import com.collegeconnect.entity.UserAccount;
import com.collegeconnect.entity.WhatsAppWebhookEvent;
import com.collegeconnect.notification.WhatsAppProperties;
import com.collegeconnect.notification.WhatsAppWebhookService;
import com.collegeconnect.repository.NotificationAttemptRepository;
import com.collegeconnect.repository.NotificationOutboxRepository;
import com.collegeconnect.repository.ParentProfileRepository;
import com.collegeconnect.repository.ParentStudentLinkRepository;
import com.collegeconnect.repository.WhatsAppConsentEventRepository;
import com.collegeconnect.repository.WhatsAppWebhookEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WhatsAppWebhookServiceTest {

    private WhatsAppProperties properties;
    private WhatsAppWebhookService service;
    private WhatsAppWebhookEventRepository webhookRepository;
    private NotificationAttemptRepository attemptRepository;
    private ParentProfileRepository parentProfileRepository;
    private ParentStudentLinkRepository parentStudentLinkRepository;
    private WhatsAppConsentEventRepository consentEventRepository;

    @BeforeEach
    void setUp() {
        properties = new WhatsAppProperties();
        properties.setAppSecret("test-app-secret");
        webhookRepository = mock(WhatsAppWebhookEventRepository.class);
        attemptRepository = mock(NotificationAttemptRepository.class);
        parentProfileRepository = mock(ParentProfileRepository.class);
        parentStudentLinkRepository = mock(ParentStudentLinkRepository.class);
        consentEventRepository = mock(WhatsAppConsentEventRepository.class);
        service = new WhatsAppWebhookService(
                properties,
                webhookRepository,
                attemptRepository,
                mock(NotificationOutboxRepository.class),
                parentProfileRepository,
                parentStudentLinkRepository,
                consentEventRepository,
                new ObjectMapper());
    }

    @Test
    void signatureValidationRequiresExactSignedPayload() throws Exception {
        String payload = "{\"entry\":[]}";
        String validSignature = signature(payload, "test-app-secret");

        assertThat(service.verifySignature(payload, validSignature)).isTrue();
        assertThat(service.verifySignature(payload + " ", validSignature)).isFalse();
        assertThat(service.verifySignature(payload, "sha256=invalid")).isFalse();
    }

    @Test
    void verificationChallengeRequiresConfiguredTokenAndSubscribeMode() {
        properties.setVerifyToken("verify-secret");

        assertThat(service.verifyChallenge("subscribe", "verify-secret")).isTrue();
        assertThat(service.verifyChallenge("subscribe", "wrong")).isFalse();
        assertThat(service.verifyChallenge("other", "verify-secret")).isFalse();
    }

    @Test
    void duplicateInboundMessageIdDoesNotCreateAnotherWebhookEvent() {
        String payload = "{\"entry\":[{\"changes\":[{\"value\":{\"messages\":["
                + "{\"id\":\"wamid.inbound-1\",\"from\":\"919876543210\","
                + "\"text\":{\"body\":\"STOP\"}}]}}]}]}";
        when(webhookRepository.existsByProviderEventId(any())).thenReturn(true);

        service.storeWebhook(payload);
        service.storeWebhook(payload);

        verify(webhookRepository, never()).save(any());
        verify(webhookRepository, org.mockito.Mockito.times(2)).flush();
    }

    @Test
    void startOptInRequiresAnActiveVerifiedLinkForTheVerifiedParent() {
        ParentProfile parent = mock(ParentProfile.class);
        UserAccount account = mock(UserAccount.class);
        ParentStudentLink inactiveLink = mock(ParentStudentLink.class);
        ParentStudentLink activeVerifiedLink = mock(ParentStudentLink.class);
        when(parent.getId()).thenReturn(17L);
        when(parent.getAccount()).thenReturn(account);
        when(parent.hasVerifiedMobile()).thenReturn(true);
        when(parent.isWhatsappOptIn()).thenReturn(false);
        when(account.getStatus()).thenReturn("ACTIVE");
        when(parentProfileRepository.findByVerifiedMobile("919876543210"))
                .thenReturn(Optional.of(parent));
        when(parentStudentLinkRepository.findByParent_Id(17L))
                .thenReturn(List.of(inactiveLink, activeVerifiedLink));
        when(inactiveLink.isActiveVerified()).thenReturn(false);
        when(activeVerifiedLink.isActiveVerified()).thenReturn(true);
        when(webhookRepository.findNextReceivedForUpdate(any())).thenReturn(List.of(
                new WhatsAppWebhookEvent(
                        "inbound-start",
                        "INBOUND",
                        "hash",
                        "{\"id\":\"wamid.start\",\"from\":\"919876543210\","
                                + "\"text\":{\"body\":\"START\"}}",
                        Instant.now())));

        service.processNextWebhook();

        verify(parentStudentLinkRepository).findByParent_Id(17L);
        verify(parent).recordWhatsappConsent(eq(true), any());
        verify(consentEventRepository).save(any());
    }

    @Test
    void startDoesNotOptInWhenParentHasNoActiveVerifiedStudentLink() {
        ParentProfile parent = mock(ParentProfile.class);
        UserAccount account = mock(UserAccount.class);
        ParentStudentLink unverifiedLink = mock(ParentStudentLink.class);
        when(parent.getId()).thenReturn(18L);
        when(parent.getAccount()).thenReturn(account);
        when(parent.hasVerifiedMobile()).thenReturn(true);
        when(parent.isWhatsappOptIn()).thenReturn(false);
        when(account.getStatus()).thenReturn("ACTIVE");
        when(parentProfileRepository.findByVerifiedMobile("919876543210"))
                .thenReturn(Optional.of(parent));
        when(parentStudentLinkRepository.findByParent_Id(18L)).thenReturn(List.of(unverifiedLink));
        when(unverifiedLink.isActiveVerified()).thenReturn(false);
        when(webhookRepository.findNextReceivedForUpdate(any())).thenReturn(List.of(
                new WhatsAppWebhookEvent(
                        "inbound-start-unlinked",
                        "INBOUND",
                        "hash",
                        "{\"id\":\"wamid.start-unlinked\",\"from\":\"919876543210\","
                                + "\"text\":{\"body\":\"START\"}}",
                        Instant.now())));

        service.processNextWebhook();

        verify(parentStudentLinkRepository).findByParent_Id(18L);
        verify(parent, never()).recordWhatsappConsent(anyBoolean(), any());
        verify(consentEventRepository, never()).save(any());
    }

    @Test
    void lateDeliveredCallbackDoesNotDowngradeReadStatus() {
        NotificationOutbox outbox = mock(NotificationOutbox.class);
        when(outbox.getId()).thenReturn(42L);
        when(outbox.getStatus()).thenReturn("READ");
        NotificationAttempt attempt = mock(NotificationAttempt.class);
        when(attempt.getId()).thenReturn(88L);
        when(attempt.getProviderMessageId()).thenReturn("wamid.outbound-1");
        when(attempt.getDeliveryStatus()).thenReturn("READ");
        when(attempt.getEvent()).thenReturn(outbox);
        WhatsAppWebhookEvent event = new WhatsAppWebhookEvent(
                "provider-event", "STATUS", "hash",
                "{\"id\":\"wamid.outbound-1\",\"status\":\"delivered\"}", Instant.now());
        when(webhookRepository.findNextReceivedForUpdate(any())).thenReturn(List.of(event));
        when(attemptRepository.findByProviderMessageId("wamid.outbound-1"))
                .thenReturn(java.util.Optional.of(attempt));
        when(attemptRepository.findByIdForUpdate(88L)).thenReturn(java.util.Optional.of(attempt));
        NotificationOutboxRepository outboxRepository = mock(NotificationOutboxRepository.class);
        when(outboxRepository.findByIdForUpdate(42L)).thenReturn(java.util.Optional.of(outbox));
        service = new WhatsAppWebhookService(
                properties,
                webhookRepository,
                attemptRepository,
                outboxRepository,
                mock(ParentProfileRepository.class),
                mock(ParentStudentLinkRepository.class),
                mock(WhatsAppConsentEventRepository.class),
                new ObjectMapper());

        service.processNextWebhook();

        verify(attempt, never()).updateDeliveryStatus(any(), any(), any(), any());
        verify(outbox, never()).markDelivered(any());
    }

    private String signature(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}

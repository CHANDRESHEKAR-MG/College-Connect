package com.collegeconnect;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.collegeconnect.entity.NotificationAttempt;
import com.collegeconnect.entity.NotificationOutbox;
import com.collegeconnect.notification.NotificationDeliveryClaim;
import com.collegeconnect.notification.NotificationDeliveryService;
import com.collegeconnect.notification.WhatsAppDeliveryException;
import com.collegeconnect.notification.WhatsAppProperties;
import com.collegeconnect.notification.WhatsAppProvider;
import com.collegeconnect.repository.NotificationAttemptRepository;
import com.collegeconnect.repository.NotificationDefaultPreferenceRepository;
import com.collegeconnect.repository.NotificationOutboxRepository;
import com.collegeconnect.repository.NotificationPreferenceRepository;
import com.collegeconnect.repository.ParentStudentLinkRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationDeliveryServiceTest {

    private NotificationOutboxRepository outboxRepository;
    private NotificationAttemptRepository attemptRepository;
    private NotificationDeliveryService service;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(NotificationOutboxRepository.class);
        attemptRepository = mock(NotificationAttemptRepository.class);
        service = new NotificationDeliveryService(
                outboxRepository,
                attemptRepository,
                mock(ParentStudentLinkRepository.class),
                mock(NotificationPreferenceRepository.class),
                mock(NotificationDefaultPreferenceRepository.class),
                new WhatsAppProperties());
    }

    @Test
    void temporaryProviderFailureSchedulesRetry() {
        NotificationOutbox event = mock(NotificationOutbox.class);
        NotificationAttempt attempt = mock(NotificationAttempt.class);
        when(event.getStatus()).thenReturn("PROCESSING");
        when(outboxRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(event));
        when(attemptRepository.findByEvent_IdAndAttemptNumber(4L, 1)).thenReturn(Optional.of(attempt));
        NotificationDeliveryClaim claim =
                new NotificationDeliveryClaim(4L, 1, "919876543210", "TPL-ATT-ABSENT", "{}", "correlation");
        WhatsAppDeliveryException failure = new WhatsAppDeliveryException(
                "provider rate limit",
                WhatsAppDeliveryException.Outcome.RETRYABLE,
                "HTTP_429",
                429,
                null);

        service.recordFailure(claim, failure);

        verify(attempt).failed(
                eq("REJECTED"), eq("HTTP_429"), eq(429), eq("provider rate limit"), any());
        verify(event).markRetryable(any(), any());
    }

    @Test
    void ambiguousProviderOutcomeIsNotAutomaticallyRetried() {
        NotificationOutbox event = mock(NotificationOutbox.class);
        NotificationAttempt attempt = mock(NotificationAttempt.class);
        when(event.getStatus()).thenReturn("PROCESSING");
        when(outboxRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(event));
        when(attemptRepository.findByEvent_IdAndAttemptNumber(5L, 1)).thenReturn(Optional.of(attempt));
        NotificationDeliveryClaim claim =
                new NotificationDeliveryClaim(5L, 1, "919876543210", "TPL-ATT-ABSENT", "{}", "correlation");
        WhatsAppDeliveryException failure = new WhatsAppDeliveryException(
                "delivery result unknown",
                WhatsAppDeliveryException.Outcome.AMBIGUOUS,
                "TRANSPORT_OUTCOME_UNKNOWN",
                null,
                null);

        service.recordFailure(claim, failure);

        verify(event).markAmbiguous(any());
        verify(event, never()).markRetryable(any(), any());
    }

    @Test
    void expiredProcessingLeaseWithoutProviderIdBecomesAmbiguous() {
        NotificationOutbox event = mock(NotificationOutbox.class);
        NotificationAttempt attempt = mock(NotificationAttempt.class);
        when(event.getId()).thenReturn(6L);
        when(event.getAttemptCount()).thenReturn(2);
        when(attemptRepository.findByEvent_IdAndAttemptNumber(6L, 2)).thenReturn(Optional.of(attempt));
        when(outboxRepository.findExpiredProcessingForUpdate(any(), any()))
                .thenReturn(List.of(event));

        service.recoverExpiredLeases();

        verify(attempt).failed(
                eq("UNKNOWN"),
                eq("WORKER_LEASE_EXPIRED"),
                eq(null),
                eq("Worker lease expired before the attempt outcome was recorded"),
                any());
        verify(event).markAmbiguous(any());
    }

    @Test
    void expiredLeaseWithKnownProviderIdIsRecoveredAsSent() {
        NotificationOutbox event = mock(NotificationOutbox.class);
        NotificationAttempt attempt = mock(NotificationAttempt.class);
        when(event.getId()).thenReturn(7L);
        when(event.getAttemptCount()).thenReturn(1);
        when(attempt.getProviderMessageId()).thenReturn("wamid.accepted");
        when(attemptRepository.findByEvent_IdAndAttemptNumber(7L, 1)).thenReturn(Optional.of(attempt));
        when(outboxRepository.findExpiredProcessingForUpdate(any(), any()))
                .thenReturn(List.of(event));

        service.recoverExpiredLeases();

        verify(event).markSent(any());
        verify(event, never()).markAmbiguous(any());
        verify(attempt, never()).failed(any(), any(), any(), any(), any());
    }

    @Test
    void acceptedResponseDoesNotOverwriteEarlierDeliveryFailureCallback() {
        NotificationOutbox event = mock(NotificationOutbox.class);
        NotificationAttempt attempt = mock(NotificationAttempt.class);
        when(event.getStatus()).thenReturn("DELIVERY_FAILED");
        when(outboxRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(event));
        when(attemptRepository.findByEvent_IdAndAttemptNumber(8L, 1)).thenReturn(Optional.of(attempt));
        NotificationDeliveryClaim claim =
                new NotificationDeliveryClaim(8L, 1, "919876543210", "TPL-ATT-ABSENT", "{}", "correlation");

        service.recordAccepted(claim, new WhatsAppProvider.SendResult("wamid.accepted", 200));

        verify(attempt).accepted(eq("wamid.accepted"), eq(200), any());
        verify(event, never()).markSent(any());
    }
}

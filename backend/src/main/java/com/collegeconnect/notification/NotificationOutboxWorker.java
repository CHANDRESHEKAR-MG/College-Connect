package com.collegeconnect.notification;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationOutboxWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationOutboxWorker.class);

    private final NotificationDeliveryService deliveryService;
    private final WhatsAppProvider provider;
    private final WhatsAppProperties properties;

    public NotificationOutboxWorker(
            NotificationDeliveryService deliveryService,
            WhatsAppProvider provider,
            WhatsAppProperties properties) {
        this.deliveryService = deliveryService;
        this.provider = provider;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${collegeconnect.whatsapp.worker-delay-ms:5000}",
            initialDelayString = "${collegeconnect.whatsapp.worker-delay-ms:5000}")
    public void processBatch() {
        deliveryService.recoverExpiredLeases();
        if (!properties.isEnabled()) {
            return;
        }
        for (int index = 0; index < properties.getBatchSize(); index++) {
            Optional<NotificationDeliveryClaim> claim = deliveryService.claimNext();
            if (claim.isEmpty()) {
                return;
            }
            send(claim.get());
        }
    }

    private void send(NotificationDeliveryClaim claim) {
        try {
            WhatsAppProvider.SendResult result = provider.send(new WhatsAppProvider.SendRequest(
                    claim.recipient(),
                    claim.templateName(),
                    claim.payloadJson(),
                    claim.correlationId()));
            deliveryService.recordAccepted(claim, result);
        } catch (WhatsAppDeliveryException exception) {
            LOGGER.warn(
                    "WhatsApp delivery attempt {} for event {} ended as {}",
                    claim.attemptNumber(),
                    claim.eventId(),
                    exception.getOutcome());
            deliveryService.recordFailure(claim, exception);
        }
    }
}

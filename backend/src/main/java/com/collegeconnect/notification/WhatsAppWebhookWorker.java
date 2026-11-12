package com.collegeconnect.notification;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppWebhookWorker {

    private final WhatsAppWebhookService webhookService;

    public WhatsAppWebhookWorker(WhatsAppWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @Scheduled(
            fixedDelayString = "${collegeconnect.whatsapp.webhook-poll-delay-ms:1000}",
            initialDelayString = "${collegeconnect.whatsapp.webhook-poll-delay-ms:1000}")
    public void processNext() {
        webhookService.processNextWebhook();
    }
}

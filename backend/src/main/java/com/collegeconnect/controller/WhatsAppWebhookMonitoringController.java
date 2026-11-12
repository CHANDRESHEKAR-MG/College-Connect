package com.collegeconnect.controller;

import com.collegeconnect.dto.WhatsAppWebhookEventResponse;
import com.collegeconnect.service.WhatsAppWebhookMonitoringService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WhatsAppWebhookMonitoringController {

    private final WhatsAppWebhookMonitoringService service;

    public WhatsAppWebhookMonitoringController(WhatsAppWebhookMonitoringService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/notifications/webhooks")
    public ResponseEntity<List<WhatsAppWebhookEventResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(service.list(status, limit));
    }
}

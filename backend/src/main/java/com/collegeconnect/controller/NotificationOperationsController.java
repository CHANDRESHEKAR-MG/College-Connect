package com.collegeconnect.controller;

import com.collegeconnect.dto.NotificationOutboxActionRequest;
import com.collegeconnect.dto.NotificationOutboxResponse;
import com.collegeconnect.dto.NotificationOutboxSummaryResponse;
import com.collegeconnect.dto.NotificationResendRequest;
import com.collegeconnect.service.NotificationOperationsService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationOperationsController {

    private final NotificationOperationsService notificationOperationsService;

    public NotificationOperationsController(NotificationOperationsService notificationOperationsService) {
        this.notificationOperationsService = notificationOperationsService;
    }

    @GetMapping("/api/admin/notifications/outbox/summary")
    public ResponseEntity<NotificationOutboxSummaryResponse> summary() {
        return ResponseEntity.ok(notificationOperationsService.summary());
    }

    @GetMapping("/api/admin/notifications/outbox")
    public ResponseEntity<List<NotificationOutboxResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(notificationOperationsService.list(status, limit));
    }

    @PostMapping("/api/admin/notifications/outbox/{eventId}/retry")
    public ResponseEntity<NotificationOutboxResponse> retry(
            @PathVariable("eventId") Long eventId,
            @Valid @RequestBody NotificationOutboxActionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(notificationOperationsService.retryConfirmed(
                currentUserId(authentication), eventId, request));
    }

    @PostMapping("/api/admin/notifications/outbox/{eventId}/resend")
    public ResponseEntity<NotificationOutboxResponse> resend(
            @PathVariable("eventId") Long eventId,
            @Valid @RequestBody NotificationResendRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(notificationOperationsService.resend(
                currentUserId(authentication), eventId, request));
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}

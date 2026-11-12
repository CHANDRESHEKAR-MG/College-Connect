package com.collegeconnect.controller;

import com.collegeconnect.dto.GlobalNotificationPreferenceRequest;
import com.collegeconnect.dto.GlobalNotificationPreferenceResponse;
import com.collegeconnect.service.GlobalNotificationPreferenceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/notification-preferences/defaults")
public class GlobalNotificationPreferenceController {

    private final GlobalNotificationPreferenceService service;

    public GlobalNotificationPreferenceController(GlobalNotificationPreferenceService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<GlobalNotificationPreferenceResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @PutMapping
    public ResponseEntity<GlobalNotificationPreferenceResponse> update(
            @Valid @RequestBody GlobalNotificationPreferenceRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(service.update(Long.valueOf(authentication.getName()), request));
    }
}

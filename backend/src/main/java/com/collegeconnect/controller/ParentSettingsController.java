package com.collegeconnect.controller;

import com.collegeconnect.dto.NotificationPreferenceRequest;
import com.collegeconnect.dto.NotificationPreferenceResponse;
import com.collegeconnect.service.ParentSettingsService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ParentSettingsController {

    private final ParentSettingsService parentSettingsService;

    public ParentSettingsController(ParentSettingsService parentSettingsService) {
        this.parentSettingsService = parentSettingsService;
    }

    @GetMapping("/api/parent/notification-preferences")
    public ResponseEntity<List<NotificationPreferenceResponse>> preferences(Authentication authentication) {
        return ResponseEntity.ok(parentSettingsService.getPreferences(currentUserId(authentication)));
    }

    @PutMapping("/api/parent/notification-preferences")
    public ResponseEntity<NotificationPreferenceResponse> updatePreference(
            @Valid @RequestBody NotificationPreferenceRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(parentSettingsService.updatePreference(currentUserId(authentication), request));
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}

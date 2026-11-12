package com.collegeconnect.service;

import com.collegeconnect.dto.GlobalNotificationPreferenceRequest;
import com.collegeconnect.dto.GlobalNotificationPreferenceResponse;
import com.collegeconnect.entity.AuditLog;
import com.collegeconnect.entity.NotificationDefaultPreference;
import com.collegeconnect.entity.UserAccount;
import com.collegeconnect.repository.AuditLogRepository;
import com.collegeconnect.repository.NotificationDefaultPreferenceRepository;
import com.collegeconnect.repository.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GlobalNotificationPreferenceService {

    private final NotificationDefaultPreferenceRepository preferenceRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuditLogRepository auditLogRepository;

    public GlobalNotificationPreferenceService(
            NotificationDefaultPreferenceRepository preferenceRepository,
            UserAccountRepository userAccountRepository,
            AuditLogRepository auditLogRepository) {
        this.preferenceRepository = preferenceRepository;
        this.userAccountRepository = userAccountRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public List<GlobalNotificationPreferenceResponse> list() {
        return preferenceRepository.findAllByOrderByCategoryAsc().stream()
                .map(this::response)
                .toList();
    }

    @Transactional
    public GlobalNotificationPreferenceResponse update(
            Long adminAccountId, GlobalNotificationPreferenceRequest request) {
        String category = request.category().trim().toUpperCase(Locale.ROOT);
        NotificationDefaultPreference preference = preferenceRepository.findByCategory(category)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Unknown notification category"));
        UserAccount admin = userAccountRepository.findById(adminAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Instant updatedAt = Instant.now();
        preference.update(request.enabled(), admin, updatedAt);
        auditLogRepository.save(new AuditLog(
                admin,
                "ADMIN",
                "NOTIFICATION_DEFAULT_UPDATED",
                "NOTIFICATION_DEFAULT_PREFERENCE",
                category,
                request.reason().trim(),
                updatedAt));
        return response(preference);
    }

    private GlobalNotificationPreferenceResponse response(NotificationDefaultPreference preference) {
        return new GlobalNotificationPreferenceResponse(
                preference.getCategory(), preference.isEnabled(), preference.getUpdatedAt());
    }
}

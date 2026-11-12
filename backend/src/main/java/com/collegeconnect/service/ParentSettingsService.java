package com.collegeconnect.service;

import com.collegeconnect.dto.NotificationPreferenceRequest;
import com.collegeconnect.dto.NotificationPreferenceResponse;
import com.collegeconnect.entity.NotificationPreference;
import com.collegeconnect.entity.ParentProfile;
import com.collegeconnect.repository.NotificationDefaultPreferenceRepository;
import com.collegeconnect.repository.NotificationPreferenceRepository;
import com.collegeconnect.repository.ParentProfileRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ParentSettingsService {

    private static final List<String> CATEGORIES = List.of(
            "ATTENDANCE_ABSENT",
            "ATTENDANCE_PRESENT",
            "ATTENDANCE_SUMMARY",
            "LEAVE",
            "LEAVE_SUBMITTED",
            "ABSENCE",
            "ABSENCE_REMINDER",
            "CIE",
            "SEE",
            "ALL_MESSAGES");

    private final ParentProfileRepository parentProfileRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final NotificationDefaultPreferenceRepository defaultPreferenceRepository;

    public ParentSettingsService(
            ParentProfileRepository parentProfileRepository,
            NotificationPreferenceRepository notificationPreferenceRepository,
            NotificationDefaultPreferenceRepository defaultPreferenceRepository) {
        this.parentProfileRepository = parentProfileRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.defaultPreferenceRepository = defaultPreferenceRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferenceResponse> getPreferences(Long accountId) {
        ParentProfile parent = getParent(accountId);
        Map<String, NotificationPreference> parentPreferences =
                notificationPreferenceRepository.findByParent_IdOrderByCategoryAsc(parent.getId()).stream()
                        .collect(Collectors.toMap(NotificationPreference::getCategory, Function.identity()));
        Map<String, Boolean> globalDefaults = defaultPreferenceRepository.findAllByOrderByCategoryAsc().stream()
                .collect(Collectors.toMap(
                        preference -> preference.getCategory(),
                        preference -> preference.isEnabled()));
        return CATEGORIES.stream()
                .map(category -> {
                    NotificationPreference preference = parentPreferences.get(category);
                    return new NotificationPreferenceResponse(
                            category,
                            preference == null
                                    ? globalDefaults.getOrDefault(category, false)
                                    : preference.isEnabled());
                })
                .toList();
    }

    @Transactional
    public NotificationPreferenceResponse updatePreference(
            Long accountId, NotificationPreferenceRequest request) {
        ParentProfile parent = getParent(accountId);
        String category = request.category().toUpperCase(Locale.ROOT);
        NotificationPreference preference = notificationPreferenceRepository
                .findByParent_IdAndCategory(parent.getId(), category)
                .orElseGet(() -> new NotificationPreference(parent, category, request.enabled()));
        preference.setEnabled(request.enabled());
        notificationPreferenceRepository.save(preference);
        return new NotificationPreferenceResponse(category, preference.isEnabled());
    }

    private ParentProfile getParent(Long accountId) {
        return parentProfileRepository.findByAccount_Id(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Parent profile not found"));
    }
}

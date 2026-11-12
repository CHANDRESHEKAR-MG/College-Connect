package com.collegeconnect.dto;

import java.time.Instant;

public record GlobalNotificationPreferenceResponse(String category, boolean enabled, Instant updatedAt) {}

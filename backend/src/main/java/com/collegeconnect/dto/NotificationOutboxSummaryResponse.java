package com.collegeconnect.dto;

import java.util.Map;

public record NotificationOutboxSummaryResponse(Map<String, Long> counts) {
}

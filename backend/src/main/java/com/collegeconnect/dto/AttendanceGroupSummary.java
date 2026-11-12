package com.collegeconnect.dto;

import java.math.BigDecimal;

public record AttendanceGroupSummary(
        String groupType,
        Long sectionId,
        String sectionName,
        Long offeringId,
        String subjectCode,
        String subjectName,
        long totalRecords,
        long present,
        long absent,
        long authorizedLeave,
        BigDecimal attendancePercentage) {}

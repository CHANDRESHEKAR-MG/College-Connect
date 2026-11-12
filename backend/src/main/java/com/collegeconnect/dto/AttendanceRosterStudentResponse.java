package com.collegeconnect.dto;

public record AttendanceRosterStudentResponse(
        Long studentId,
        String collegeIdentifier,
        String displayName,
        boolean approvedLeaveForSessionDate) {
}

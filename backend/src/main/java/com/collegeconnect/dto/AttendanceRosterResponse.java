package com.collegeconnect.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AttendanceRosterResponse(
        Long sessionId,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        String status,
        String subjectCode,
        String subjectName,
        List<AttendanceRosterStudentResponse> students) {
}

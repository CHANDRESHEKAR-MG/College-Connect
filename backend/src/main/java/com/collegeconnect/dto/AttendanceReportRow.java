package com.collegeconnect.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record AttendanceReportRow(
        Long attendanceId,
        Long studentId,
        String studentIdentifier,
        String studentName,
        Long sectionId,
        String sectionName,
        Long offeringId,
        String subjectCode,
        String subjectName,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        String status) {}

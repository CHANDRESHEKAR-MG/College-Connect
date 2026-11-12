package com.collegeconnect.service;

import com.collegeconnect.dto.AttendanceSummaryResponse;
import com.collegeconnect.entity.AttendanceRecord;
import com.collegeconnect.entity.ParentProfile;
import com.collegeconnect.entity.ParentStudentLink;
import com.collegeconnect.entity.StudentProfile;
import com.collegeconnect.repository.AttendanceRecordRepository;
import com.collegeconnect.repository.ParentProfileRepository;
import com.collegeconnect.repository.ParentStudentLinkRepository;
import com.collegeconnect.repository.StudentProfileRepository;
import com.collegeconnect.security.Role;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AttendanceReportService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final ParentProfileRepository parentProfileRepository;
    private final ParentStudentLinkRepository parentStudentLinkRepository;

    public AttendanceReportService(
            AttendanceRecordRepository attendanceRecordRepository,
            StudentProfileRepository studentProfileRepository,
            ParentProfileRepository parentProfileRepository,
            ParentStudentLinkRepository parentStudentLinkRepository) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.parentProfileRepository = parentProfileRepository;
        this.parentStudentLinkRepository = parentStudentLinkRepository;
    }

    @Transactional(readOnly = true)
    public AttendanceSummaryResponse getSummary(Role role, Long accountId, Long studentId) {
        StudentProfile student = studentProfileRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
        if (role == Role.STUDENT) {
            if (!student.getAccount().getId().equals(accountId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
            }
        } else if (role == Role.PARENT) {
            ParentProfile parent = parentProfileRepository.findByAccount_Id(accountId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Parent profile not found"));
            boolean linked = parentStudentLinkRepository.findByParent_IdAndStudent_Id(parent.getId(), studentId)
                    .stream().anyMatch(ParentStudentLink::isActiveVerified);
            if (!linked) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No active verified link to this student");
            }
        } else if (role != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        List<AttendanceRecord> records = attendanceRecordRepository.findByStudent_Id(studentId);
        int present = (int) records.stream().filter(record -> "PRESENT".equalsIgnoreCase(record.getStatus())).count();
        int absent = (int) records.stream().filter(record -> "ABSENT".equalsIgnoreCase(record.getStatus())).count();
        int submitted = present + absent;
        BigDecimal percentage = submitted == 0
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(present)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(submitted), 2, RoundingMode.HALF_UP);
        return new AttendanceSummaryResponse(
                studentId, student.getDisplayName(), submitted, present, absent, percentage);
    }

    @Transactional(readOnly = true)
    public AttendanceSummaryResponse getOwnSummary(Long accountId) {
        StudentProfile student = studentProfileRepository.findByAccount_Id(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Student profile not found"));
        return getSummary(Role.STUDENT, accountId, student.getId());
    }
}

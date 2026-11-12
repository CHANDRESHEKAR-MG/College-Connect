package com.collegeconnect.service;

import com.collegeconnect.dto.AttendanceReportRow;
import com.collegeconnect.dto.AttendanceGroupSummary;
import com.collegeconnect.entity.AttendanceRecord;
import com.collegeconnect.entity.ParentProfile;
import com.collegeconnect.entity.ParentStudentLink;
import com.collegeconnect.entity.StudentProfile;
import com.collegeconnect.repository.AttendanceRecordRepository;
import com.collegeconnect.repository.ClassSessionRepository;
import com.collegeconnect.repository.ParentProfileRepository;
import com.collegeconnect.repository.ParentStudentLinkRepository;
import com.collegeconnect.repository.ProfessorAssignmentRepository;
import com.collegeconnect.repository.StudentProfileRepository;
import com.collegeconnect.security.Role;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AttendanceRangeReportService {

    private static final int MAX_ROWS = 5000;

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final ParentProfileRepository parentProfileRepository;
    private final ParentStudentLinkRepository parentStudentLinkRepository;
    private final ProfessorAssignmentRepository professorAssignmentRepository;
    private final ClassSessionRepository classSessionRepository;

    public AttendanceRangeReportService(
            AttendanceRecordRepository attendanceRecordRepository,
            StudentProfileRepository studentProfileRepository,
            ParentProfileRepository parentProfileRepository,
            ParentStudentLinkRepository parentStudentLinkRepository,
            ProfessorAssignmentRepository professorAssignmentRepository,
            ClassSessionRepository classSessionRepository) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.parentProfileRepository = parentProfileRepository;
        this.parentStudentLinkRepository = parentStudentLinkRepository;
        this.professorAssignmentRepository = professorAssignmentRepository;
        this.classSessionRepository = classSessionRepository;
    }

    @Transactional(readOnly = true)
    public List<AttendanceReportRow> report(
            Role role,
            Long accountId,
            Long studentId,
            Long sectionId,
            Long offeringId,
            LocalDate startDate,
            LocalDate endDate) {
        validateDates(startDate, endDate);
        Long scopedStudentId = studentId;
        Long scopedProfessorId = null;
        if (role == Role.STUDENT) {
            if (sectionId != null || offeringId != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Student reports are self-scoped");
            }
            StudentProfile student = studentProfileRepository.findByAccount_Id(accountId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Student profile not found"));
            if (studentId != null && !student.getId().equals(studentId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
            }
            scopedStudentId = student.getId();
        } else if (role == Role.PARENT) {
            if (sectionId != null || offeringId != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent reports are student-scoped");
            }
            if (studentId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A linked student id is required");
            }
            ParentProfile parent = parentProfileRepository.findByAccount_Id(accountId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Parent profile not found"));
            boolean linked = parentStudentLinkRepository.findByParent_IdAndStudent_Id(parent.getId(), studentId)
                    .stream().anyMatch(ParentStudentLink::isActiveVerified);
            if (!linked) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No active verified link to this student");
            }
        } else if (role == Role.PROFESSOR) {
            scopedProfessorId = accountId;
            if (offeringId != null
                    && !professorAssignmentRepository
                            .existsByOffering_IdAndProfessor_Account_IdAndStatusIgnoreCase(
                                    offeringId, accountId, "ACTIVE")
                    && !classSessionRepository.hasAssignedSessionForOfferingAndDateRange(
                            accountId, offeringId, startDate, endDate)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No active assignment to this offering");
            }
            if (sectionId != null && !classSessionRepository.hasAssignedSessionForSectionAndDateRange(
                    accountId, sectionId, startDate, endDate)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No assigned sessions in this section");
            }
        } else if (role != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        List<AttendanceRecord> records = attendanceRecordRepository.findDateRangeReport(
                        startDate,
                        endDate,
                        scopedStudentId,
                        role == Role.STUDENT || role == Role.PARENT ? null : sectionId,
                        role == Role.STUDENT || role == Role.PARENT ? null : offeringId,
                        scopedProfessorId,
                        PageRequest.of(0, MAX_ROWS + 1));
        if (records.size() > MAX_ROWS) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE, "Report exceeds 5000 rows; narrow the filters");
        }
        return records
                .stream()
                .map(this::toRow)
                .toList();
    }

    public String toCsv(List<AttendanceReportRow> rows) {
        StringBuilder csv = new StringBuilder(
                "Attendance ID,Student ID,Student Identifier,Student Name,Section ID,Section,"
                        + "Offering ID,Subject Code,Subject,Date,Start Time,End Time,Status\r\n");
        for (AttendanceReportRow row : rows) {
            csv.append(row.attendanceId()).append(',')
                    .append(row.studentId()).append(',')
                    .append(cell(row.studentIdentifier())).append(',')
                    .append(cell(row.studentName())).append(',')
                    .append(row.sectionId()).append(',')
                    .append(cell(row.sectionName())).append(',')
                    .append(row.offeringId()).append(',')
                    .append(cell(row.subjectCode())).append(',')
                    .append(cell(row.subjectName())).append(',')
                    .append(row.sessionDate()).append(',')
                    .append(row.startTime()).append(',')
                    .append(row.endTime()).append(',')
                    .append(cell(row.status())).append("\r\n");
        }
        return csv.toString();
    }

    @Transactional(readOnly = true)
    public List<AttendanceGroupSummary> summary(
            Role role,
            Long accountId,
            Long sectionId,
            Long offeringId,
            LocalDate startDate,
            LocalDate endDate,
            String groupType) {
        String normalizedGroup = groupType == null ? "" : groupType.toUpperCase(Locale.ROOT);
        if (!"SECTION".equals(normalizedGroup) && !"SUBJECT".equals(normalizedGroup)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "groupType must be SECTION or SUBJECT");
        }
        List<AttendanceReportRow> rows = report(
                role, accountId, null, sectionId, offeringId, startDate, endDate);
        Map<String, long[]> totals = new LinkedHashMap<>();
        Map<String, AttendanceReportRow> groupExamples = new LinkedHashMap<>();
        for (AttendanceReportRow row : rows) {
            String key = "SECTION".equals(normalizedGroup)
                    ? row.sectionId().toString()
                    : row.offeringId().toString();
            long[] counts = totals.computeIfAbsent(key, ignored -> new long[4]);
            switch (row.status().toUpperCase(Locale.ROOT)) {
                case "PRESENT" -> counts[1]++;
                case "ABSENT" -> counts[2]++;
                case "AUTHORIZED_LEAVE" -> counts[3]++;
                default -> {
                    continue;
                }
            }
            counts[0]++;
            groupExamples.putIfAbsent(key, row);
        }
        return totals.entrySet().stream()
                .map(entry -> {
                    AttendanceReportRow example = groupExamples.get(entry.getKey());
                    long[] counts = entry.getValue();
                    long eligibleRecords = counts[1] + counts[2];
                    BigDecimal percentage = eligibleRecords == 0
                            ? BigDecimal.ZERO.setScale(2)
                            : BigDecimal.valueOf(counts[1] * 100)
                                    .divide(BigDecimal.valueOf(eligibleRecords), 2, RoundingMode.HALF_UP);
                    return new AttendanceGroupSummary(
                            normalizedGroup,
                            example.sectionId(),
                            example.sectionName(),
                            "SUBJECT".equals(normalizedGroup) ? example.offeringId() : null,
                            "SUBJECT".equals(normalizedGroup) ? example.subjectCode() : null,
                            "SUBJECT".equals(normalizedGroup) ? example.subjectName() : null,
                            counts[0],
                            counts[1],
                            counts[2],
                            counts[3],
                            percentage);
                })
                .toList();
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid report date range");
        }
    }

    private AttendanceReportRow toRow(AttendanceRecord record) {
        var session = record.getSession();
        var offering = session.getAssignment().getOffering();
        return new AttendanceReportRow(
                record.getId(),
                record.getStudent().getId(),
                record.getStudent().getCollegeIdentifier(),
                record.getStudent().getDisplayName(),
                offering.getSection().getId(),
                offering.getSection().getName(),
                offering.getId(),
                offering.getSubject().getCode(),
                offering.getSubject().getName(),
                session.getSessionDate(),
                session.getStartTime(),
                session.getEndTime(),
                record.getStatus());
    }

    private String cell(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}

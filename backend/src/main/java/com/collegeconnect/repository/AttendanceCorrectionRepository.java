package com.collegeconnect.repository;

import com.collegeconnect.entity.AttendanceCorrection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrection, Long> {

    @Query("select coalesce(max(c.correctionSequence), 0) from AttendanceCorrection c "
            + "where c.attendance.id = :attendanceId")
    int findHighestSequence(@Param("attendanceId") Long attendanceId);

    List<AttendanceCorrection> findByAttendance_IdOrderByCorrectionSequenceAsc(Long attendanceId);
}

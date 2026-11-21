package com.gym.springapp.repository;

import com.gym.springapp.model.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepo extends JpaRepository<Attendance, Long> {
    List<Attendance> findByMemberId(Long id);

    Optional<Attendance> findByMemberIdAndAttendanceDate(Long id, LocalDate attendanceDate);

    long countByAttendanceDate(LocalDate attendanceDate);

    List<Attendance> findByAttendanceDate(LocalDate attendanceDate);

    List<Attendance> findByAttendanceDateBetween(LocalDate start, LocalDate end);

    default Optional<Attendance> findByMemberIdAndDate(Long id, LocalDate date) {
        return findByMemberIdAndAttendanceDate(id, date);
    }

    default long countByDate(LocalDate date) {
        return countByAttendanceDate(date);
    }
}

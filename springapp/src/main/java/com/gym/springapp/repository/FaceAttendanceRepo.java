package com.gym.springapp.repository;

import com.gym.springapp.model.FaceAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FaceAttendanceRepo extends JpaRepository<FaceAttendance, Long> {
    Optional<FaceAttendance> findByUserIdAndDate(Long userId, LocalDate date);
    List<FaceAttendance> findByUserIdAndCheckOutTimeIsNull(Long userId);
    List<FaceAttendance> findByDate(LocalDate date);
    List<FaceAttendance> findByUserId(Long userId);
    List<FaceAttendance> findByUserIdOrderByCheckInTimeDesc(Long userId);
    long countByUserIdAndDateBetween(Long userId, LocalDate start, LocalDate end);
    List<FaceAttendance> findByDateBetween(LocalDate start, LocalDate end);
    List<FaceAttendance> findAllByOrderByCheckInTimeDesc();
    long countByDate(LocalDate date);
    long countByDateAndRole(LocalDate date, String role);
}

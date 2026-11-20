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
    Optional<Attendance> findByMemberIdAndDate(Long id, LocalDate date);
    long countByDate(LocalDate date);
}

package com.gym.springapp.repository;

import com.gym.springapp.model.GymSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.Optional;

@Repository
public interface GymScheduleRepo extends JpaRepository<GymSchedule, Long> {
    Optional<GymSchedule> findByDayOfWeek(DayOfWeek dayOfWeek);
}

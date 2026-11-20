package com.gym.springapp.repository;

import com.gym.springapp.model.WorkoutPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkoutPlanRepo extends JpaRepository<WorkoutPlan, Long> {
    Optional<WorkoutPlan> findByMemberId(Long memberId);
    Optional<WorkoutPlan> findByMemberUserEmail(String email);
}

package com.gym.springapp.repository;

import com.gym.springapp.model.WorkoutPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkoutPlanRepo extends JpaRepository<WorkoutPlan, Long> {
    Optional<WorkoutPlan> findByMemberId(Long memberId);
    Optional<WorkoutPlan> findByMemberUserEmail(String email);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM WorkoutPlan wp WHERE wp.member.id = :memberId")
    void deleteDirectByMemberId(@Param("memberId") Long memberId);
}

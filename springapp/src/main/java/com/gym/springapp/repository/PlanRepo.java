package com.gym.springapp.repository;

import com.gym.springapp.model.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanRepo extends JpaRepository<Plan, Long> {
    boolean existsByName(String name);
}

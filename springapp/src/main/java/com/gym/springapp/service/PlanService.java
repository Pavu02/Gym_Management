package com.gym.springapp.service;

import com.gym.springapp.model.Plan;

import java.util.List;

public interface PlanService {
    Plan addPlan(Plan plan);
    List<Plan> getAllPlans();
    Plan getPlanById(Long id);
    Plan updatePlan(Long id, Plan plan);
    void deletePlan(Long id);
}

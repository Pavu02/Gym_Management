package com.gym.springapp.service;

import com.gym.springapp.exceptions.BadRequestException;
import com.gym.springapp.exceptions.DuplicateResourceException;
import com.gym.springapp.exceptions.ResourceNotFoundException;
import com.gym.springapp.model.Member;
import com.gym.springapp.model.Plan;
import com.gym.springapp.repository.MemberRepo;
import com.gym.springapp.repository.PlanRepo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlanServiceImpl implements PlanService {

    private final PlanRepo planRepo;
    private final MemberRepo memberRepo;

    public PlanServiceImpl(PlanRepo planRepo, MemberRepo memberRepo) {
        this.planRepo = planRepo;
        this.memberRepo = memberRepo;
    }

    @Override
    public Plan addPlan(Plan plan) {
        if (planRepo.existsByName(plan.getName())) {
            throw new DuplicateResourceException("Plan already exists with name: " + plan.getName());
        }
        return planRepo.save(plan);
    }

    @Override
    public List<Plan> getAllPlans() {
        return planRepo.findAll();
    }

    @Override
    public Plan getPlanById(Long id) {
        return planRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found with id: " + id));
    }

    @Override
    public Plan updatePlan(Long id, Plan planDetails) {
        Plan existingPlan = getPlanById(id);

        if (!existingPlan.getName().equalsIgnoreCase(planDetails.getName()) &&
                planRepo.existsByName(planDetails.getName())) {
            throw new DuplicateResourceException("Plan already exists with name: " + planDetails.getName());
        }

        existingPlan.setName(planDetails.getName());
        existingPlan.setDurationMonths(planDetails.getDurationMonths());
        existingPlan.setPrice(planDetails.getPrice());
        existingPlan.setDescription(planDetails.getDescription());
        existingPlan.setFeatures(planDetails.getFeatures());
        existingPlan.setPopular(planDetails.isPopular());

        return planRepo.save(existingPlan);
    }

    @Override
    public void deletePlan(Long id) {
        Plan plan = getPlanById(id);

        List<Member> assignedMembers = memberRepo.findByPlanId(id);
        if (!assignedMembers.isEmpty()) {
            throw new BadRequestException("Plan is assigned to members");
        }

        planRepo.delete(plan);
    }
}

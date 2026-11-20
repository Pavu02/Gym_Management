package com.gym.springapp.service;

import com.gym.springapp.dto.*;
import com.gym.springapp.model.Plan;
import com.gym.springapp.model.Trainer;

import java.util.List;

public interface OnboardingService {
    OnboardingStatusDTO getStatus(String email);
    List<Plan> getPlansSortedByPrice();
    List<Trainer> getAllTrainers();
    void enroll(String email, EnrollRequestDTO request);
    void setupProfile(String email, ProfileSetupDTO request);
    OnboardingSummaryDTO getSummary(String email);
    MemberResponseDTO completePayment(String email, OnboardingPaymentDTO request);
}

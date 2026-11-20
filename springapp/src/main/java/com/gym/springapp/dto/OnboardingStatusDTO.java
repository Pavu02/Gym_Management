package com.gym.springapp.dto;

public record OnboardingStatusDTO(
    boolean hasMember,
    boolean onboardingCompleted,
    String status,
    Long planId,
    Long trainerId
) {}

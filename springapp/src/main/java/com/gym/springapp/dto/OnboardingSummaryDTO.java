package com.gym.springapp.dto;

public record OnboardingSummaryDTO(
    String planName,
    double planPrice,
    int durationMonths,
    String trainerName,
    double trainerFee,
    double totalAmount
) {}

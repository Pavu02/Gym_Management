package com.gym.springapp.dto;

import java.time.LocalDate;

public record MemberProfileUpdateDTO(
    String name,
    String phone,
    String gender,
    LocalDate dateOfBirth,
    double heightCm,
    double weightKg,
    String fitnessGoal,
    String medicalConditions,
    String emergencyContactName,
    String emergencyContactPhone
) {}

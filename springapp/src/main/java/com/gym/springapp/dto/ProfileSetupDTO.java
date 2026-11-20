package com.gym.springapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record ProfileSetupDTO(
    @NotBlank(message = "Gender is required")
    String gender,

    @Past(message = "Date of birth must be in the past")
    LocalDate dateOfBirth,

    @Positive(message = "Height must be positive")
    double heightCm,

    @Positive(message = "Weight must be positive")
    double weightKg,

    @NotBlank(message = "Fitness goal is required")
    String fitnessGoal,

    String medicalConditions,

    @NotBlank(message = "Emergency contact name is required")
    String emergencyContactName,

    @Pattern(regexp = "^\\d{10}$", message = "Emergency contact phone must be 10 digits")
    String emergencyContactPhone
) {}

package com.gym.springapp.dto;

import java.time.Instant;
import java.time.LocalDate;

public record MemberResponseDTO(
                Long id,
                String name,
                String email,
                String phone,
                String planName,
                String trainerName,
                LocalDate joinDate,
                LocalDate expiryDate,
                String status,
                Long planId,
                Long trainerId,
                String gender,
                LocalDate dateOfBirth,
                double heightCm,
                double weightKg,
                Double bmi,
                String fitnessGoal,
                String medicalConditions,
                String emergencyContactName,
                String emergencyContactPhone,
                boolean onboardingCompleted,
                Long daysLeft,
                boolean active,
                boolean faceEnabled,
                boolean faceEnrolled,
                boolean faceReenrollmentRequired,
                int activeBiometricDevices,
                boolean hasOpenAttendance,
                Instant deactivatedAt,
                String deactivationReason,
                String role,
                Long version) {
}

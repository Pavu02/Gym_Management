package com.gym.springapp.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record FaceCheckinResponseDTO(
        Long userId,
        String name,
        String role,
        String photo,
        String phone,
        String email,
        String planName,
        LocalDate planStartDate,
        LocalDate planEndDate,
        Long remainingDays,
        String assignedTrainerName,
        Long totalAttendanceThisMonth,
        LocalDateTime checkInTime,
        String status, // "CHECKED_IN", "ALREADY_CHECKED_IN", "MEMBERSHIP_EXPIRED", "NO_PLAN"
        boolean expiringSoon,
        boolean alreadyCheckedIn
) {}

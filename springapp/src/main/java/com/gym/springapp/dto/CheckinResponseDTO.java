package com.gym.springapp.dto;

public record CheckinResponseDTO(
    String status,
    String message,
    Long memberId,
    String memberName,
    String memberInitials,
    String planName,
    Long remainingDays,
    String warningMessage,
    String trainerName,
    String specialRequirements,
    String checkInTime,
    String checkOutTime,
    String method
) {}

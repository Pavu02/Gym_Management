package com.gym.springapp.dto;

public record MemberStatusActionResponseDTO(
        boolean success,
        Long memberId,
        String status,
        String message,
        Long version,
        boolean closedOpenAttendance,
        boolean biometricReenrollmentRequired
) {}


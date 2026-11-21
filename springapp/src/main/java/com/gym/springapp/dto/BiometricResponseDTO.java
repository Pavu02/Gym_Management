package com.gym.springapp.dto;

import java.time.LocalDateTime;

public record BiometricResponseDTO(
    String status,
    String message,
    Long memberId,
    LocalDateTime enrolledAt
) {}

package com.gym.springapp.dto;

import java.time.LocalDateTime;

public record FingerprintStatusDTO(
    boolean enrolled,
    Long userId,
    String fingerprintHash,
    Integer qualityScore,
    String deviceName,
    LocalDateTime enrolledAt
) {}

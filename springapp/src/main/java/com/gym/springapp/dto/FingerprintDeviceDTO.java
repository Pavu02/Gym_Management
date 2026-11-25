package com.gym.springapp.dto;

import java.time.LocalDateTime;

public record FingerprintDeviceDTO(
    Long id,
    String deviceName,
    LocalDateTime createdAt,
    LocalDateTime lastUsedAt
) {}

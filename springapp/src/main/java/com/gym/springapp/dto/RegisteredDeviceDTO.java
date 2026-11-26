package com.gym.springapp.dto;

import java.time.Instant;

public record RegisteredDeviceDTO(
        Long id,
        String deviceName,
        String deviceType,
        String transports,
        Instant registeredAt,
        Instant lastUsedAt,
        boolean active,
        Instant revokedAt
) {}

package com.gym.springapp.dto;

import java.time.LocalDateTime;

public record WebAuthnDeviceDTO(
    Long id,
    String credentialId,
    String deviceName,
    long signCount,
    LocalDateTime createdAt
) {}

package com.gym.springapp.dto;

public record FingerprintEnrollRequestDTO(
    String fingerprintHash,
    String minutiaeData,
    Integer qualityScore,
    String deviceName
) {}

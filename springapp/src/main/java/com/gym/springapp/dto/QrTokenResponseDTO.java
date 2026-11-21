package com.gym.springapp.dto;

public record QrTokenResponseDTO(
    String token,
    long expiresInSeconds,
    long timestamp
) {}

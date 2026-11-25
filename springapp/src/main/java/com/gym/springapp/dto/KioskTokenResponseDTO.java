package com.gym.springapp.dto;

import java.time.LocalDateTime;

public record KioskTokenResponseDTO(
    String token,
    LocalDateTime expiresAt,
    String qrUrl
) {}

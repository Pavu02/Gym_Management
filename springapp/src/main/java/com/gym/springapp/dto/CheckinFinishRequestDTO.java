package com.gym.springapp.dto;

public record CheckinFinishRequestDTO(
    String requestId,
    String kioskToken,
    String credential
) {}

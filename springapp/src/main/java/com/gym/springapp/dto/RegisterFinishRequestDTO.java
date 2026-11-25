package com.gym.springapp.dto;

public record RegisterFinishRequestDTO(
    String requestId,
    String credential,
    String deviceName
) {}

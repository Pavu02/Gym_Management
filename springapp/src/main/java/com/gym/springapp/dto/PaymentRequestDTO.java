package com.gym.springapp.dto;

public record PaymentRequestDTO(
    Long memberId,
    double amount,
    String mode,
    String status
) {}

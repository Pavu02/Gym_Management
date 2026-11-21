package com.gym.springapp.dto;

public record PasskeyAssertionVerifyDTO(
    String assertionResponseJson,
    Long memberId,
    String qrToken
) {}

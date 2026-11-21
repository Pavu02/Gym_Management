package com.gym.springapp.dto;

public record PasskeyRegistrationOptionsDTO(
    String rpId,
    String rpName,
    String challenge,
    String userId,
    String userName,
    String optionsJson
) {}

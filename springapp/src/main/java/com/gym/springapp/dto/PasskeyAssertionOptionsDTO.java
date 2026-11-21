package com.gym.springapp.dto;

public record PasskeyAssertionOptionsDTO(
    String challenge,
    String rpId,
    String optionsJson
) {}

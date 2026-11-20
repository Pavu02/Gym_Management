package com.gym.springapp.dto;

import java.time.LocalDate;

public record MemberResponseDTO(
        Long id,
        String name,
        String email,
        String phone,
        String planName,
        String trainerName,
        LocalDate joinDate,
        LocalDate expiryDate,
        String status) {
}

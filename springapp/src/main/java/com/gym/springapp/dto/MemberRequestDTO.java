package com.gym.springapp.dto;

public record MemberRequestDTO(
        String name,
        String email,
        String password,
        String phone,
        Long planId,
        Long trainerId) {
}

package com.gym.springapp.dto;

public record TrainerRequestDTO(
    String name,
    String email,
    String password,
    String phone,
    String specialization,
    int experienceYears
) {}

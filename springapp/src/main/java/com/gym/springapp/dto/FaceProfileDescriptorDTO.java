package com.gym.springapp.dto;

import java.util.List;

public record FaceProfileDescriptorDTO(
        Long userId,
        String name,
        String role,
        List<Double> descriptor
) {}

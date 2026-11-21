package com.gym.springapp.dto;

import java.util.List;

public record FaceCheckinRequestDTO(
        Long userId,
        List<Double> descriptor
) {}

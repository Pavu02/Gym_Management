package com.gym.springapp.dto;

import java.util.List;

public record FaceEnrollmentRequestDTO(
    List<Double> descriptor,
    Boolean consent,
    String password,
    Long memberId
) {}

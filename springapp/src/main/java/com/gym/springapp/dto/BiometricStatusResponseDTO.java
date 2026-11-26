package com.gym.springapp.dto;

import java.time.LocalDateTime;

public record BiometricStatusResponseDTO(
    boolean faceEnrolled,
    LocalDateTime faceEnrolledAt,
    boolean consentGiven,
    LocalDateTime consentAt,
    int passkeyCount,
    boolean fingerprintEnrolled,
    LocalDateTime fingerprintEnrolledAt,
    boolean faceEnabled,
    boolean faceReenrollmentRequired,
    boolean biometricReenrollmentRequired
) {}

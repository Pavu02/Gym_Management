package com.gym.springapp.dto;

import java.time.LocalDate;

public record CheckinResultDTO(
    String action, // "CHECK_IN" or "CHECK_OUT"
    String time,
    String memberName,
    String planName,
    LocalDate expiryDate,
    Long daysLeft,
    String status
) {}

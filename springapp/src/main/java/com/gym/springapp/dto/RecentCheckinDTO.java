package com.gym.springapp.dto;

import java.time.LocalDate;

public record RecentCheckinDTO(
    String memberName,
    String memberInitials,
    String planName,
    LocalDate expiryDate,
    String status,
    Long daysLeft,
    String checkInMethod,
    String time,
    String action
) {}

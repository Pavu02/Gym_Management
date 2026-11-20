package com.gym.springapp.dto;

public record DashboardStatsDTO(
    long totalMembers,
    long activeMembers,
    long expiredMembers,
    long pendingMembers,
    long totalTrainers,
    long totalPlans,
    long todayAttendance,
    double monthlyRevenue
) {}

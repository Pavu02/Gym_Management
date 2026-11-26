package com.gym.springapp.dto;

import java.util.List;

public record DashboardAnalyticsDTO(
        List<MonthlyRevenue> revenueByMonth,
        List<DayAttendance> attendanceLast7Days,
        CheckInsByMethod checkInsByMethod,
        MembersByStatus membersByStatus,
        List<PlanCount> membersByPlan,
        List<MonthlyCount> newMembersByMonth,
        List<PeakHour> peakHours,
        List<TrainerLoad> trainerLoad
) {
    public record MonthlyRevenue(String label, double amount) {}
    public record DayAttendance(String label, long count) {}
    public record CheckInsByMethod(long face, long fingerprint, long manual) {}
    public record MembersByStatus(long active, long expired, long pendingOnboarding) {}
    public record PlanCount(String plan, long count) {}
    public record MonthlyCount(String label, long count) {}
    public record PeakHour(int hour, long count) {}
    public record TrainerLoad(String name, long members, int max) {}
}

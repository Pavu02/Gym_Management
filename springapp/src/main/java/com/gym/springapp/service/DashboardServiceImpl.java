package com.gym.springapp.service;

import com.gym.springapp.dto.DashboardStatsDTO;
import com.gym.springapp.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final MemberRepo memberRepo;
    private final TrainerRepo trainerRepo;
    private final PlanRepo planRepo;
    private final AttendanceRepo attendanceRepo;
    private final PaymentRepo paymentRepo;

    public DashboardServiceImpl(MemberRepo memberRepo,
                                TrainerRepo trainerRepo,
                                PlanRepo planRepo,
                                AttendanceRepo attendanceRepo,
                                PaymentRepo paymentRepo) {
        this.memberRepo = memberRepo;
        this.trainerRepo = trainerRepo;
        this.planRepo = planRepo;
        this.attendanceRepo = attendanceRepo;
        this.paymentRepo = paymentRepo;
    }

    @Override
    public DashboardStatsDTO getStats() {
        long totalMembers = memberRepo.countByDeletedFalse();
        long activeMembers = memberRepo.countActiveByStatus("ACTIVE");
        long expiredMembers = memberRepo.countActiveByStatus("EXPIRED");
        long pendingMembers = memberRepo.countPendingOnboarding();
        long totalTrainers = trainerRepo.count();
        long totalPlans = planRepo.count();
        long todayAttendance = attendanceRepo.countByDate(LocalDate.now());

        LocalDate now = LocalDate.now();
        Double revenue = paymentRepo.sumPaidByMonth(now.getMonthValue(), now.getYear());
        double monthlyRevenue = (revenue != null) ? revenue : 0.0;

        return new DashboardStatsDTO(
                totalMembers,
                activeMembers,
                expiredMembers,
                pendingMembers,
                totalTrainers,
                totalPlans,
                todayAttendance,
                monthlyRevenue
        );
    }
}

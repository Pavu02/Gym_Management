package com.gym.springapp.service;

import com.gym.springapp.dto.DashboardAnalyticsDTO;
import com.gym.springapp.dto.DashboardAnalyticsDTO.*;
import com.gym.springapp.model.Attendance;
import com.gym.springapp.model.AttendanceMethod;
import com.gym.springapp.model.Member;
import com.gym.springapp.repository.AttendanceRepo;
import com.gym.springapp.repository.MemberRepo;
import com.gym.springapp.repository.PaymentRepo;
import com.gym.springapp.repository.TrainerRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardAnalyticsService {

    private final MemberRepo memberRepo;
    private final PaymentRepo paymentRepo;
    private final AttendanceRepo attendanceRepo;
    private final TrainerRepo trainerRepo;
    private final ZoneId zoneId;

    public DashboardAnalyticsService(
            MemberRepo memberRepo,
            PaymentRepo paymentRepo,
            AttendanceRepo attendanceRepo,
            TrainerRepo trainerRepo,
            @Value("${app.timezone:Asia/Kolkata}") String timezone) {
        this.memberRepo = memberRepo;
        this.paymentRepo = paymentRepo;
        this.attendanceRepo = attendanceRepo;
        this.trainerRepo = trainerRepo;
        this.zoneId = resolveZone(timezone);
    }

    private ZoneId resolveZone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            return ZoneId.of("Asia/Kolkata");
        }
    }

    public DashboardAnalyticsDTO getAnalytics() {
        LocalDate today = LocalDate.now(zoneId);
        return new DashboardAnalyticsDTO(
                buildRevenueByMonth(today),
                buildAttendanceLast7Days(today),
                buildCheckInsByMethod(today),
                buildMembersByStatus(),
                buildMembersByPlan(),
                buildNewMembersByMonth(today),
                buildPeakHours(today),
                buildTrainerLoad());
    }

    private List<MonthlyRevenue> buildRevenueByMonth(LocalDate today) {
        List<MonthlyRevenue> result = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDate month = today.minusMonths(i);
            double amount = Optional.ofNullable(
                    paymentRepo.sumPaidOrSuccessByMonth(month.getMonthValue(), month.getYear()))
                    .orElse(0.0);
            String label = month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            result.add(new MonthlyRevenue(label, amount));
        }
        return result;
    }

    private List<DayAttendance> buildAttendanceLast7Days(LocalDate today) {
        LocalDate start = today.minusDays(6);
        Map<LocalDate, Long> countsByDate = new HashMap<>();
        for (Object[] row : attendanceRepo.countActiveByDateBetween(start, today)) {
            if (row[0] instanceof LocalDate date && row[1] instanceof Number cnt) {
                countsByDate.put(date, cnt.longValue());
            }
        }
        List<DayAttendance> result = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            long count = countsByDate.getOrDefault(day, 0L);
            String label = day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            result.add(new DayAttendance(label, count));
        }
        return result;
    }

    private CheckInsByMethod buildCheckInsByMethod(LocalDate today) {
        LocalDate start = today.minusDays(29);
        long face = 0L;
        long fp = 0L;
        long manual = 0L;
        for (Object[] row : attendanceRepo.countActiveByMethodBetween(start, today)) {
            if (row[0] instanceof AttendanceMethod method && row[1] instanceof Number cnt) {
                long value = cnt.longValue();
                if (method == AttendanceMethod.FACE) {
                    face += value;
                } else if (method == AttendanceMethod.FINGERPRINT) {
                    fp += value;
                } else {
                    manual += value;
                }
            }
        }
        return new CheckInsByMethod(face, fp, manual);
    }

    private MembersByStatus buildMembersByStatus() {
        long active = memberRepo.countActiveByStatus("ACTIVE");
        long expired = memberRepo.countActiveByStatus("EXPIRED");
        long pending = memberRepo.countPendingOnboarding();
        return new MembersByStatus(active, expired, pending);
    }

    private List<PlanCount> buildMembersByPlan() {
        List<PlanCount> result = new ArrayList<>();
        for (Object[] row : memberRepo.countMembersByPlan()) {
            if (row[0] != null && row[1] instanceof Number cnt) {
                result.add(new PlanCount(row[0].toString(), cnt.longValue()));
            }
        }
        return result;
    }

    private List<MonthlyCount> buildNewMembersByMonth(LocalDate today) {
        LocalDate start = today.minusMonths(5).withDayOfMonth(1);
        List<Member> recent = memberRepo.findActiveJoinedBetween(start, today);
        Map<String, Long> byYearMonth = recent.stream()
                .filter(m -> m.getJoinDate() != null)
                .collect(Collectors.groupingBy(
                        m -> m.getJoinDate().getYear() + "-" + m.getJoinDate().getMonthValue(),
                        Collectors.counting()));

        List<MonthlyCount> result = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDate month = today.minusMonths(i);
            String key = month.getYear() + "-" + month.getMonthValue();
            String label = month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            result.add(new MonthlyCount(label, byYearMonth.getOrDefault(key, 0L)));
        }
        return result;
    }

    private List<PeakHour> buildPeakHours(LocalDate today) {
        LocalDate start = today.minusDays(29);
        List<Attendance> records = attendanceRepo.findActiveByAttendanceDateBetween(start, today);
        Map<Integer, Long> byHour = records.stream()
                .filter(a -> a.getCheckInTime() != null)
                .collect(Collectors.groupingBy(a -> a.getCheckInTime().getHour(), Collectors.counting()));
        List<PeakHour> result = new ArrayList<>();
        for (int h = 5; h <= 23; h++) {
            result.add(new PeakHour(h, byHour.getOrDefault(h, 0L)));
        }
        return result;
    }

    private List<TrainerLoad> buildTrainerLoad() {
        return trainerRepo.findByActiveTrueAndDeletedFalse().stream()
                .map(t -> {
                    String name = (t.getUser() != null && !t.getUser().isDeleted())
                            ? t.getUser().getName()
                            : "Trainer";
                    long members = memberRepo.countByTrainerIdAndDeletedFalse(t.getId());
                    int max = t.getMaxMembers() > 0 ? t.getMaxMembers() : 15;
                    return new TrainerLoad(name, members, max);
                })
                .sorted(Comparator.comparingLong(TrainerLoad::members).reversed())
                .collect(Collectors.toList());
    }
}

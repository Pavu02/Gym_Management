package com.gym.springapp.service;

import com.gym.springapp.dto.FaceCheckinResponseDTO;
import com.gym.springapp.exceptions.BadRequestException;
import com.gym.springapp.exceptions.ResourceNotFoundException;
import com.gym.springapp.model.*;
import com.gym.springapp.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class FaceAttendanceServiceImpl implements FaceAttendanceService {

    private static final ZoneId KOLKATA_ZONE = ZoneId.of("Asia/Kolkata");

    private final FaceAttendanceRepo faceAttendanceRepo;
    private final AttendanceRepo attendanceRepo;
    private final UserRepo userRepo;
    private final MemberRepo memberRepo;
    private final TrainerRepo trainerRepo;

    public FaceAttendanceServiceImpl(
            FaceAttendanceRepo faceAttendanceRepo,
            AttendanceRepo attendanceRepo,
            UserRepo userRepo,
            MemberRepo memberRepo,
            TrainerRepo trainerRepo) {
        this.faceAttendanceRepo = faceAttendanceRepo;
        this.attendanceRepo = attendanceRepo;
        this.userRepo = userRepo;
        this.memberRepo = memberRepo;
        this.trainerRepo = trainerRepo;
    }

    @Override
    @Transactional
    public FaceCheckinResponseDTO faceCheckin(Long userId) {
        return processCheckin(userId, "FACE");
    }

    @Override
    @Transactional
    public FaceCheckinResponseDTO manualCheckin(Long userId) {
        return processCheckin(userId, "MANUAL");
    }

    private FaceCheckinResponseDTO processCheckin(Long userId, String method) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            var memberOpt = memberRepo.findById(userId);
            if (memberOpt.isPresent() && memberOpt.get().getUser() != null) {
                user = memberOpt.get().getUser();
                userId = user.getId();
            }
        }
        if (user == null) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }

        LocalDate today = LocalDate.now(KOLKATA_ZONE);
        LocalDateTime now = LocalDateTime.now(KOLKATA_ZONE);
        String roleStr = user.getRole() != null ? user.getRole().name() : "MEMBER";

        // Check if user already checked in today
        Optional<FaceAttendance> existingToday = faceAttendanceRepo.findByUserIdAndDate(userId, today);
        if (existingToday.isPresent()) {
            FaceAttendance existing = existingToday.get();
            return buildResponse(user, existing.getCheckInTime(), "ALREADY_CHECKED_IN", true, false);
        }

        if ("MEMBER".equalsIgnoreCase(roleStr)) {
            Member member = memberRepo.findByUserId(userId)
                    .orElse(memberRepo.findByUserEmail(user.getEmail()).orElse(null));

            if (member == null || member.getPlan() == null || "PENDING_PAYMENT".equalsIgnoreCase(member.getStatus())) {
                return buildResponse(user, null, "NO_PLAN", false, false);
            }

            LocalDate expiryDate = member.getExpiryDate();
            long remainingDays = expiryDate != null ? ChronoUnit.DAYS.between(today, expiryDate) : -1;

            if (remainingDays < 0) {
                member.setStatus("EXPIRED");
                memberRepo.save(member);
                return buildResponse(user, null, "MEMBERSHIP_EXPIRED", false, false);
            }

            boolean expiringSoon = remainingDays <= 5;

            // Record face attendance
            FaceAttendance fa = new FaceAttendance();
            fa.setUserId(userId);
            fa.setUserName(user.getName());
            fa.setRole("MEMBER");
            fa.setCheckInTime(now);
            fa.setDate(today);
            fa.setMethod(method);
            faceAttendanceRepo.save(fa);

            // Also keep legacy Attendance table in sync
            if (attendanceRepo.findByMemberIdAndAttendanceDate(member.getId(), today).isEmpty()) {
                Attendance att = new Attendance();
                att.setMember(member);
                att.setAttendanceDate(today);
                att.setCheckInTime(LocalTime.now(KOLKATA_ZONE));
                att.setMethod("FACE".equalsIgnoreCase(method) ? AttendanceMethod.FACE : AttendanceMethod.MANUAL);
                attendanceRepo.save(att);
            }

            return buildResponse(user, now, "CHECKED_IN", false, expiringSoon);

        } else if ("TRAINER".equalsIgnoreCase(roleStr)) {
            // Trainers always record attendance (no plan check)
            FaceAttendance fa = new FaceAttendance();
            fa.setUserId(userId);
            fa.setUserName(user.getName());
            fa.setRole("TRAINER");
            fa.setCheckInTime(now);
            fa.setDate(today);
            fa.setMethod(method);
            faceAttendanceRepo.save(fa);

            return buildResponse(user, now, "CHECKED_IN", false, false);
        } else {
            // ADMIN or other role check-in
            FaceAttendance fa = new FaceAttendance();
            fa.setUserId(userId);
            fa.setUserName(user.getName());
            fa.setRole(roleStr);
            fa.setCheckInTime(now);
            fa.setDate(today);
            fa.setMethod(method);
            faceAttendanceRepo.save(fa);

            return buildResponse(user, now, "CHECKED_IN", false, false);
        }
    }

    private FaceCheckinResponseDTO buildResponse(
            User user,
            LocalDateTime checkInTime,
            String status,
            boolean alreadyCheckedIn,
            boolean expiringSoon) {

        LocalDate today = LocalDate.now(KOLKATA_ZONE);
        LocalDate startOfMonth = today.withDayOfMonth(1);
        long monthlyCount = faceAttendanceRepo.countByUserIdAndDateBetween(user.getId(), startOfMonth, today);

        String roleStr = user.getRole() != null ? user.getRole().name() : "MEMBER";

        String planName = null;
        LocalDate planStartDate = null;
        LocalDate planEndDate = null;
        Long remainingDays = null;
        String assignedTrainerName = null;

        if ("MEMBER".equalsIgnoreCase(roleStr)) {
            Member member = memberRepo.findByUserId(user.getId())
                    .orElse(memberRepo.findByUserEmail(user.getEmail()).orElse(null));

            if (member != null) {
                if (member.getPlan() != null) {
                    planName = member.getPlan().getName();
                }
                planStartDate = member.getJoinDate();
                planEndDate = member.getExpiryDate();
                if (planEndDate != null) {
                    remainingDays = ChronoUnit.DAYS.between(today, planEndDate);
                    if (remainingDays <= 5 && remainingDays >= 0) {
                        expiringSoon = true;
                    }
                }
                if (member.getTrainer() != null && member.getTrainer().getUser() != null) {
                    assignedTrainerName = member.getTrainer().getUser().getName();
                }
            }
        }

        return new FaceCheckinResponseDTO(
                user.getId(),
                user.getName(),
                roleStr,
                null, // photo
                user.getPhone(),
                user.getEmail(),
                planName,
                planStartDate,
                planEndDate,
                remainingDays,
                assignedTrainerName,
                monthlyCount,
                checkInTime,
                status,
                expiringSoon,
                alreadyCheckedIn
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<FaceCheckinResponseDTO> getTodayAttendance() {
        LocalDate today = LocalDate.now(KOLKATA_ZONE);
        List<FaceAttendance> list = faceAttendanceRepo.findByDate(today);
        List<FaceCheckinResponseDTO> dtos = new ArrayList<>();

        for (FaceAttendance fa : list) {
            User user = userRepo.findById(fa.getUserId()).orElse(null);
            if (user != null) {
                dtos.add(buildResponse(user, fa.getCheckInTime(), "CHECKED_IN", false, false));
            }
        }
        return dtos;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FaceCheckinResponseDTO> getAllAttendance(LocalDate from, LocalDate to, String role, String name) {
        List<FaceAttendance> list;
        if (from != null && to != null) {
            list = faceAttendanceRepo.findByDateBetween(from, to);
        } else {
            list = faceAttendanceRepo.findAllByOrderByCheckInTimeDesc();
        }

        List<FaceCheckinResponseDTO> dtos = new ArrayList<>();
        for (FaceAttendance fa : list) {
            if (role != null && !role.isBlank() && !role.equalsIgnoreCase("ALL") && !fa.getRole().equalsIgnoreCase(role)) {
                continue;
            }
            if (name != null && !name.isBlank() && !fa.getUserName().toLowerCase().contains(name.toLowerCase())) {
                continue;
            }

            User user = userRepo.findById(fa.getUserId()).orElse(null);
            if (user != null) {
                dtos.add(buildResponse(user, fa.getCheckInTime(), "CHECKED_IN", false, false));
            }
        }
        return dtos;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FaceCheckinResponseDTO> getMyAttendance(String authEmail) {
        User user = userRepo.findByEmailIgnoreCase(authEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + authEmail));

        List<FaceAttendance> list = faceAttendanceRepo.findByUserIdOrderByCheckInTimeDesc(user.getId());
        List<FaceCheckinResponseDTO> dtos = new ArrayList<>();

        for (FaceAttendance fa : list) {
            dtos.add(buildResponse(user, fa.getCheckInTime(), "CHECKED_IN", false, false));
        }

        // If no records in faceAttendance yet, check legacy Attendance table for members
        if (dtos.isEmpty() && "MEMBER".equalsIgnoreCase(user.getRole() != null ? user.getRole().name() : "")) {
            Member member = memberRepo.findByUserId(user.getId()).orElse(null);
            if (member != null) {
                List<Attendance> legacy = attendanceRepo.findByMemberId(member.getId());
                for (Attendance att : legacy) {
                    LocalDateTime checkInLdt = att.getAttendanceDate() != null && att.getCheckInTime() != null
                            ? att.getAttendanceDate().atTime(att.getCheckInTime())
                            : LocalDateTime.now(KOLKATA_ZONE);
                    dtos.add(buildResponse(user, checkInLdt, "CHECKED_IN", false, false));
                }
            }
        }

        return dtos;
    }
}

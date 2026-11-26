package com.gym.springapp.service;

import com.gym.springapp.dto.FaceCheckinResponseDTO;
import com.gym.springapp.exceptions.ForbiddenOperationException;
import com.gym.springapp.exceptions.MemberDeactivatedException;
import com.gym.springapp.exceptions.QrSessionConflictException;
import com.gym.springapp.exceptions.ResourceNotFoundException;
import com.gym.springapp.exceptions.VerificationRuleException;
import com.gym.springapp.model.*;
import com.gym.springapp.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    private final FaceProfileRepo faceProfileRepo;
    private final MemberBiometricRepo memberBiometricRepo;
    private final AttendanceService attendanceService;
    private final MemberAuditService memberAuditService;

    public FaceAttendanceServiceImpl(
            FaceAttendanceRepo faceAttendanceRepo,
            AttendanceRepo attendanceRepo,
            UserRepo userRepo,
            MemberRepo memberRepo,
            TrainerRepo trainerRepo,
            FaceProfileRepo faceProfileRepo,
            MemberBiometricRepo memberBiometricRepo,
            AttendanceService attendanceService,
            MemberAuditService memberAuditService) {
        this.faceAttendanceRepo = faceAttendanceRepo;
        this.attendanceRepo = attendanceRepo;
        this.userRepo = userRepo;
        this.memberRepo = memberRepo;
        this.trainerRepo = trainerRepo;
        this.faceProfileRepo = faceProfileRepo;
        this.memberBiometricRepo = memberBiometricRepo;
        this.attendanceService = attendanceService;
        this.memberAuditService = memberAuditService;
    }

    @Override
    @Transactional
    public FaceCheckinResponseDTO faceCheckin(Long userId) {
        return processCheckin(userId, "FACE");
    }

    @Override
    @Transactional
    public FaceCheckinResponseDTO fingerprintCheckin(Long userId) {
        return processCheckin(userId, "FINGERPRINT");
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

        if ("MEMBER".equalsIgnoreCase(roleStr)) {
            Member member = memberRepo.findByUserId(userId)
                    .orElse(memberRepo.findByUserEmail(user.getEmail()).orElse(null));

            if (member == null) {
                return buildResponse(user, null, "NO_PLAN", false, false);
            }

            // Central eligibility check runs FIRST — DEACTIVATED members can never be
            // checked in
            // even if face recognition matched a historical/cached template.
            try {
                attendanceService.validateMemberCheckInEligibility(member, method);
            } catch (MemberDeactivatedException | ForbiddenOperationException ex) {
                throw ex;
            } catch (VerificationRuleException ex) {
                String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
                if (msg.contains("onboarding") || member.getPlan() == null
                        || "PENDING_PAYMENT".equalsIgnoreCase(member.getStatus())) {
                    return buildResponse(user, null, "NO_PLAN", false, false);
                }
                return buildResponse(user, null, "MEMBERSHIP_EXPIRED", false, false);
            } catch (QrSessionConflictException ex) {
                Optional<FaceAttendance> existingToday = faceAttendanceRepo.findByUserIdAndDate(userId, today);
                LocalDateTime time = existingToday.map(FaceAttendance::getCheckInTime).orElse(now);
                return buildResponse(user, time, "ALREADY_CHECKED_IN", true, false);
            }

            // If checking in via FACE, require Face ID to be enabled (not disabled after
            // deactivation/reactivation)
            if ("FACE".equalsIgnoreCase(method)) {
                boolean profileDisabled = faceProfileRepo.findByUserId(user.getId())
                        .map(fp -> !fp.isEnabled())
                        .orElse(false)
                        || memberBiometricRepo.findByMemberId(member.getId())
                                .map(mb -> !mb.isEnabled())
                                .orElse(false);

                if (!member.isFaceEnabled() || profileDisabled) {
                    memberAuditService.logDeniedEntry(member.getId(), "FACE", "FACE_REENROLLMENT_REQUIRED");
                    throw new MemberDeactivatedException(
                            "FACE_REENROLLMENT_REQUIRED",
                            member.getId(),
                            user.getName(),
                            "Face ID registration required");
                }
            }

            long remainingDays = member.getExpiryDate() != null ? ChronoUnit.DAYS.between(today, member.getExpiryDate())
                    : 0;
            boolean expiringSoon = remainingDays <= 5;

            AttendanceMethod attMethod = AttendanceMethod.MANUAL;
            CheckInMethod checkInMethod = CheckInMethod.MANUAL;
            if ("FACE".equalsIgnoreCase(method)) {
                attMethod = AttendanceMethod.FACE;
                checkInMethod = CheckInMethod.FACE;
            } else if ("FINGERPRINT".equalsIgnoreCase(method)) {
                attMethod = AttendanceMethod.FINGERPRINT;
                checkInMethod = CheckInMethod.FINGERPRINT;
            }

            Instant nowInstant = Instant.now();
            Attendance att = new Attendance();
            att.setMember(member);
            att.setAttendanceDate(today);
            att.setCheckInTime(LocalTime.now(KOLKATA_ZONE));
            att.setMethod(attMethod);
            att.setCheckInMethod(checkInMethod);
            att.setGymId(member.getGymId());
            att.setBranchId(member.getBranchId());
            att.setCheckInTimestamp(nowInstant);
            att.setVerificationMethodLabel(method.toUpperCase());
            att.setVerificationResult("VERIFIED_" + method.toUpperCase());
            att.setCreatedAt(nowInstant);
            attendanceRepo.save(att);

            FaceAttendance fa = faceAttendanceRepo.findByUserIdAndDate(userId, today)
                    .orElseGet(FaceAttendance::new);
            fa.setUserId(userId);
            fa.setUserName(user.getName());
            fa.setRole("MEMBER");
            fa.setCheckInTime(now);
            fa.setCheckOutTime(null);
            fa.setDate(today);
            fa.setMethod(method);
            faceAttendanceRepo.save(fa);

            return buildResponse(user, now, "CHECKED_IN", false, expiringSoon);

        } else {
            if (!user.isActive() || user.isDeleted()) {
                throw new MemberDeactivatedException("USER_DEACTIVATED", user.getId(), user.getName(),
                        "Access Denied — Account Deactivated");
            }

            Optional<FaceAttendance> existingToday = faceAttendanceRepo.findByUserIdAndDate(userId, today);
            if (existingToday.isPresent()) {
                return buildResponse(user, existingToday.get().getCheckInTime(), "ALREADY_CHECKED_IN", true, false);
            }

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
                alreadyCheckedIn);
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
            if (role != null && !role.isBlank() && !role.equalsIgnoreCase("ALL")
                    && !fa.getRole().equalsIgnoreCase(role)) {
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

        // If no records in faceAttendance yet, check legacy Attendance table for
        // members
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

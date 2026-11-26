package com.gym.springapp.service;

import com.gym.springapp.config.CheckInPolicyProperties;
import com.gym.springapp.exceptions.BadRequestException;
import com.gym.springapp.exceptions.ForbiddenOperationException;
import com.gym.springapp.exceptions.QrSessionConflictException;
import com.gym.springapp.exceptions.ResourceNotFoundException;
import com.gym.springapp.exceptions.VerificationRuleException;
import com.gym.springapp.model.*;
import com.gym.springapp.repository.AttendanceRepo;
import com.gym.springapp.repository.FaceAttendanceRepo;
import com.gym.springapp.repository.MemberRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
public class AttendanceServiceImpl implements AttendanceService {

    private static final ZoneId KOLKATA_ZONE = ZoneId.of("Asia/Kolkata");

    private final AttendanceRepo attendanceRepo;
    private final MemberRepo memberRepo;
    private final FaceAttendanceRepo faceAttendanceRepo;
    private final CheckInPolicyProperties policy;

    public AttendanceServiceImpl(
            AttendanceRepo attendanceRepo,
            MemberRepo memberRepo,
            FaceAttendanceRepo faceAttendanceRepo,
            CheckInPolicyProperties policy) {
        this.attendanceRepo = attendanceRepo;
        this.memberRepo = memberRepo;
        this.faceAttendanceRepo = faceAttendanceRepo;
        this.policy = policy;
    }

    @Override
    public void validateMemberEligibility(Member member) {
        if (member == null || member.isDeleted() || !member.isActive()
                || member.getUser() == null || member.getUser().isDeleted() || !member.getUser().isActive()
                || "SUSPENDED".equalsIgnoreCase(member.getStatus())
                || "INACTIVE".equalsIgnoreCase(member.getStatus())) {
            throw new ForbiddenOperationException("Your member account is inactive, suspended, or deleted.");
        }

        if ("PENDING_PAYMENT".equalsIgnoreCase(member.getStatus()) || !member.isOnboardingCompleted()
                || member.getPlan() == null) {
            throw new VerificationRuleException("Complete your membership onboarding before checking in.");
        }

        LocalDate today = LocalDate.now(KOLKATA_ZONE);
        if (member.getExpiryDate() != null && member.getExpiryDate().isBefore(today)) {
            member.setStatus("EXPIRED");
            memberRepo.save(member);
            throw new VerificationRuleException("Your membership has expired. Please contact the gym administrator.");
        }

        if ("EXPIRED".equalsIgnoreCase(member.getStatus())) {
            throw new VerificationRuleException("Your membership has expired. Please contact the gym administrator.");
        }

        Optional<Attendance> latestToday = attendanceRepo
                .findFirstByMemberIdAndAttendanceDateOrderByIdDesc(member.getId(), today);
        if (latestToday.isPresent()) {
            Attendance existing = latestToday.get();
            if (existing.getCheckOutTime() == null) {
                throw new QrSessionConflictException("You are already checked in.");
            }
            if (!policy.isAllowReentryAfterCheckout()) {
                throw new QrSessionConflictException("Already checked in and checked out today.");
            }
        }
    }

    @Override
    @Transactional
    public Attendance recordVerifiedCheckIn(
            Member member,
            String gymId,
            String branchId,
            AttendanceMethod method,
            CheckInMethod checkInMethod,
            String verificationMethodLabel,
            String verificationResult,
            String riskResult,
            Long qrSessionId,
            Long deviceCredentialId,
            String idempotencyKey,
            String fallbackReason) {
        validateMemberEligibility(member);

        if (qrSessionId != null && attendanceRepo.existsByMemberIdAndQrSessionId(member.getId(), qrSessionId)) {
            throw new QrSessionConflictException("Attendance was already recorded for this check-in session.");
        }

        LocalDate today = LocalDate.now(KOLKATA_ZONE);
        LocalTime nowTime = LocalTime.now(KOLKATA_ZONE);
        Instant nowInstant = Instant.now();

        Attendance attendance = new Attendance();
        attendance.setMember(member);
        attendance.setAttendanceDate(today);
        attendance.setCheckInTime(nowTime);
        attendance.setMethod(method);
        attendance.setCheckInMethod(checkInMethod);
        attendance.setGymId(gymId);
        attendance.setBranchId(branchId);
        attendance.setCheckInTimestamp(nowInstant);
        attendance.setVerificationMethodLabel(verificationMethodLabel);
        attendance.setVerificationResult(verificationResult);
        attendance.setRiskResult(riskResult);
        attendance.setQrSessionId(qrSessionId);
        attendance.setDeviceCredentialId(deviceCredentialId);
        attendance.setIdempotencyKey(idempotencyKey);
        attendance.setFallbackReason(fallbackReason);
        attendance.setCreatedAt(nowInstant);

        Attendance saved = attendanceRepo.save(attendance);

        // Keep FaceAttendance projection synchronized (method.name() <= 20 chars for pre-migration schemas)
        Optional<FaceAttendance> existingProjection = faceAttendanceRepo
                .findByUserIdAndDate(member.getUser().getId(), today);
        if (existingProjection.isEmpty()) {
            FaceAttendance fa = new FaceAttendance();
            fa.setUserId(member.getUser().getId());
            fa.setUserName(member.getUser().getName());
            fa.setRole("MEMBER");
            fa.setDate(today);
            fa.setCheckInTime(LocalDateTime.now(KOLKATA_ZONE));
            fa.setMethod(method.name());
            faceAttendanceRepo.save(fa);
        } else {
            FaceAttendance fa = existingProjection.get();
            fa.setCheckOutTime(null);
            fa.setCheckInTime(LocalDateTime.now(KOLKATA_ZONE));
            fa.setMethod(method.name());
            faceAttendanceRepo.save(fa);
        }

        return saved;
    }

    @Override
    public Attendance checkIn(Long memberId) {
        Member member = memberRepo.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found with id: " + memberId));

        if ("PENDING_PAYMENT".equalsIgnoreCase(member.getStatus()) || !member.isOnboardingCompleted()) {
            throw new BadRequestException("Complete your membership first");
        }

        LocalDate today = LocalDate.now(KOLKATA_ZONE);
        if (member.getExpiryDate() != null && member.getExpiryDate().isBefore(today)) {
            member.setStatus("EXPIRED");
            memberRepo.save(member);
        }

        if ("EXPIRED".equalsIgnoreCase(member.getStatus())) {
            throw new BadRequestException("Membership expired");
        }

        Optional<Attendance> latestToday = attendanceRepo.findFirstByMemberIdAndAttendanceDateOrderByIdDesc(memberId,
                today);
        if (latestToday.isPresent()) {
            Attendance existing = latestToday.get();
            if (existing.getCheckOutTime() == null) {
                throw new BadRequestException("Already checked in today");
            }
            if (!policy.isAllowReentryAfterCheckout()) {
                throw new BadRequestException("Already checked in today");
            }
        }

        Attendance attendance = new Attendance();
        attendance.setMember(member);
        attendance.setAttendanceDate(today);
        attendance.setCheckInTime(LocalTime.now(KOLKATA_ZONE));
        attendance.setMethod(AttendanceMethod.MANUAL);

        return attendanceRepo.save(attendance);
    }

    @Override
    public Attendance checkOut(Long memberId) {
        if (!memberRepo.existsById(memberId)) {
            throw new ResourceNotFoundException("Member not found with id: " + memberId);
        }

        LocalDate today = LocalDate.now(KOLKATA_ZONE);
        Attendance attendance = attendanceRepo.findFirstByMemberIdAndAttendanceDateOrderByIdDesc(memberId, today)
                .orElseThrow(() -> new ResourceNotFoundException("No check-in record found for today"));

        if (attendance.getCheckOutTime() != null) {
            throw new BadRequestException("Already checked out");
        }

        attendance.setCheckOutTime(LocalTime.now(KOLKATA_ZONE));
        return attendanceRepo.save(attendance);
    }

    @Override
    public Attendance checkInByEmail(String email) {
        Member member = memberRepo.findByUserEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Member profile not found for email: " + email));
        return checkIn(member.getId());
    }

    @Override
    public Attendance checkOutByEmail(String email) {
        Member member = memberRepo.findByUserEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Member profile not found for email: " + email));
        return checkOut(member.getId());
    }

    @Override
    public List<Attendance> getAllAttendance() {
        return attendanceRepo.findAll();
    }

    @Override
    public List<Attendance> getAttendanceByMember(Long memberId) {
        return attendanceRepo.findByMemberId(memberId);
    }

    @Override
    public List<Attendance> getMyAttendance(String email) {
        Member member = memberRepo.findByUserEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Member profile not found for email: " + email));
        return attendanceRepo.findByMemberId(member.getId());
    }
}

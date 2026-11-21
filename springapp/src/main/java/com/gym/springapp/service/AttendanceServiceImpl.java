package com.gym.springapp.service;

import com.gym.springapp.exceptions.BadRequestException;
import com.gym.springapp.exceptions.ResourceNotFoundException;
import com.gym.springapp.model.Attendance;
import com.gym.springapp.model.AttendanceMethod;
import com.gym.springapp.model.Member;
import com.gym.springapp.repository.AttendanceRepo;
import com.gym.springapp.repository.MemberRepo;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class AttendanceServiceImpl implements AttendanceService {

    private static final ZoneId KOLKATA_ZONE = ZoneId.of("Asia/Kolkata");

    private final AttendanceRepo attendanceRepo;
    private final MemberRepo memberRepo;

    public AttendanceServiceImpl(AttendanceRepo attendanceRepo, MemberRepo memberRepo) {
        this.attendanceRepo = attendanceRepo;
        this.memberRepo = memberRepo;
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

        if (attendanceRepo.findByMemberIdAndAttendanceDate(memberId, today).isPresent()) {
            throw new BadRequestException("Already checked in today");
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
        Attendance attendance = attendanceRepo.findByMemberIdAndAttendanceDate(memberId, today)
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

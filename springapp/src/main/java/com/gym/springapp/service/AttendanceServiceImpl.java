package com.gym.springapp.service;

import com.gym.springapp.exceptions.BadRequestException;
import com.gym.springapp.exceptions.ResourceNotFoundException;
import com.gym.springapp.model.Attendance;
import com.gym.springapp.model.Member;
import com.gym.springapp.repository.AttendanceRepo;
import com.gym.springapp.repository.MemberRepo;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
public class AttendanceServiceImpl implements AttendanceService {

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

        LocalDate today = LocalDate.now();
        if (member.getExpiryDate() != null && member.getExpiryDate().isBefore(today)) {
            member.setStatus("EXPIRED");
            memberRepo.save(member);
        }

        if ("EXPIRED".equalsIgnoreCase(member.getStatus())) {
            throw new BadRequestException("Membership expired");
        }

        if (attendanceRepo.findByMemberIdAndDate(memberId, today).isPresent()) {
            throw new BadRequestException("Already checked in today");
        }

        Attendance attendance = new Attendance();
        attendance.setMember(member);
        attendance.setDate(today);
        attendance.setCheckIn(LocalTime.now());

        return attendanceRepo.save(attendance);
    }

    @Override
    public Attendance checkOut(Long memberId) {
        if (!memberRepo.existsById(memberId)) {
            throw new ResourceNotFoundException("Member not found with id: " + memberId);
        }

        LocalDate today = LocalDate.now();
        Attendance attendance = attendanceRepo.findByMemberIdAndDate(memberId, today)
                .orElseThrow(() -> new ResourceNotFoundException("No check-in record found for today"));

        if (attendance.getCheckOut() != null) {
            throw new BadRequestException("Already checked out");
        }

        attendance.setCheckOut(LocalTime.now());
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

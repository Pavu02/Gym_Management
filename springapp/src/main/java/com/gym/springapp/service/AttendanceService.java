package com.gym.springapp.service;

import com.gym.springapp.model.Attendance;
import com.gym.springapp.model.AttendanceMethod;
import com.gym.springapp.model.CheckInMethod;
import com.gym.springapp.model.Member;

import java.util.List;

public interface AttendanceService {
    Attendance checkIn(Long memberId);
    Attendance checkOut(Long memberId);
    Attendance checkInByEmail(String email);
    Attendance checkOutByEmail(String email);
    List<Attendance> getAllAttendance();
    List<Attendance> getAttendanceByMember(Long memberId);
    List<Attendance> getMyAttendance(String email);

    void validateMemberEligibility(Member member);

    void validateMemberCheckInEligibility(Member member);

    void validateMemberCheckInEligibility(Member member, String entryMethod);

    void validateAccountNotDeactivated(Member member, String entryMethod);

    Attendance recordVerifiedCheckIn(
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
            String fallbackReason);
}

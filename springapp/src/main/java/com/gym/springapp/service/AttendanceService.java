package com.gym.springapp.service;

import com.gym.springapp.model.Attendance;

import java.util.List;

public interface AttendanceService {
    Attendance checkIn(Long memberId);
    Attendance checkOut(Long memberId);
    Attendance checkInByEmail(String email);
    Attendance checkOutByEmail(String email);
    List<Attendance> getAllAttendance();
    List<Attendance> getAttendanceByMember(Long memberId);
    List<Attendance> getMyAttendance(String email);
}

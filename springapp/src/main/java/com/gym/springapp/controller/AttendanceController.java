package com.gym.springapp.controller;

import com.gym.springapp.model.Attendance;
import com.gym.springapp.service.AttendanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping("/check-in/{memberId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Attendance> checkIn(@PathVariable Long memberId) {
        return ResponseEntity.ok(attendanceService.checkIn(memberId));
    }

    @PostMapping("/check-out/{memberId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Attendance> checkOut(@PathVariable Long memberId) {
        return ResponseEntity.ok(attendanceService.checkOut(memberId));
    }

    @PostMapping("/me/check-in")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<Attendance> checkInMe(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(attendanceService.checkInByEmail(email));
    }

    @PostMapping("/me/check-out")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<Attendance> checkOutMe(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(attendanceService.checkOutByEmail(email));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Attendance>> getAllAttendance() {
        return ResponseEntity.ok(attendanceService.getAllAttendance());
    }

    @GetMapping("/member/{memberId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TRAINER')")
    public ResponseEntity<List<Attendance>> getAttendanceByMember(@PathVariable Long memberId) {
        return ResponseEntity.ok(attendanceService.getAttendanceByMember(memberId));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<List<Attendance>> getMyAttendance(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(attendanceService.getMyAttendance(email));
    }
}

package com.gym.springapp.controller;

import com.gym.springapp.dto.FaceCheckinRequestDTO;
import com.gym.springapp.dto.FaceCheckinResponseDTO;
import com.gym.springapp.model.Attendance;
import com.gym.springapp.service.AttendanceService;
import com.gym.springapp.service.FaceAttendanceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final FaceAttendanceService faceAttendanceService;

    public AttendanceController(AttendanceService attendanceService, FaceAttendanceService faceAttendanceService) {
        this.attendanceService = attendanceService;
        this.faceAttendanceService = faceAttendanceService;
    }

    // --- Face Recognition & Modern Attendance Endpoints ---

    @PostMapping("/face-checkin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FaceCheckinResponseDTO> faceCheckin(@RequestBody FaceCheckinRequestDTO request) {
        return ResponseEntity.ok(faceAttendanceService.faceCheckin(request.userId()));
    }

    @PostMapping("/manual-checkin/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FaceCheckinResponseDTO> manualCheckin(@PathVariable Long userId) {
        return ResponseEntity.ok(faceAttendanceService.manualCheckin(userId));
    }

    @GetMapping("/today")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<FaceCheckinResponseDTO>> getTodayAttendance() {
        return ResponseEntity.ok(faceAttendanceService.getTodayAttendance());
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<FaceCheckinResponseDTO>> getAllAttendanceFiltered(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String name) {
        return ResponseEntity.ok(faceAttendanceService.getAllAttendance(from, to, role, name));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('MEMBER', 'TRAINER')")
    public ResponseEntity<List<FaceCheckinResponseDTO>> getMyFaceAttendance(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(faceAttendanceService.getMyAttendance(email));
    }

    // --- Legacy Endpoints Kept for Full Backward Compatibility ---

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

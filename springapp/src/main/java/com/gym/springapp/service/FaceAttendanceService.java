package com.gym.springapp.service;

import com.gym.springapp.dto.FaceCheckinResponseDTO;

import java.time.LocalDate;
import java.util.List;

public interface FaceAttendanceService {
    FaceCheckinResponseDTO faceCheckin(Long userId);
    FaceCheckinResponseDTO manualCheckin(Long userId);
    List<FaceCheckinResponseDTO> getTodayAttendance();
    List<FaceCheckinResponseDTO> getAllAttendance(LocalDate from, LocalDate to, String role, String name);
    List<FaceCheckinResponseDTO> getMyAttendance(String authEmail);
}

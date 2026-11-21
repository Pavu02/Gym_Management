package com.gym.springapp.service;

import com.gym.springapp.dto.BiometricResponseDTO;
import com.gym.springapp.dto.BiometricStatusResponseDTO;
import com.gym.springapp.dto.FaceEnrollmentRequestDTO;
import com.gym.springapp.model.Member;

import java.util.List;

public interface BiometricService {
    BiometricResponseDTO enrollFace(FaceEnrollmentRequestDTO request, String authEmail, boolean isAdmin);
    BiometricStatusResponseDTO getBiometricStatus(Long memberId, String authEmail, boolean isAdmin);
    void deleteBiometrics(Long memberId, String authEmail, boolean isAdmin);
    Member findMatchingMemberByFace(List<Double> queryDescriptor);
}

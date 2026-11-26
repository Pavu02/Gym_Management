package com.gym.springapp.service;

import com.gym.springapp.dto.*;

import java.util.List;

public interface FingerprintService {
    RegisterStartResponseDTO startRegistration(String authEmail);
    FingerprintDeviceDTO finishRegistration(String authEmail, RegisterFinishRequestDTO request);
    List<FingerprintDeviceDTO> getMemberDevices(String authEmail);
    void deleteMyDevice(String authEmail, Long deviceId);
    List<FingerprintDeviceDTO> getDevicesByMemberId(Long memberId);
    void adminDeleteDevice(Long deviceId);

    CheckinStartResponseDTO startCheckin(String authEmail, CheckinStartRequestDTO request);
    CheckinResultDTO finishCheckin(String authEmail, CheckinFinishRequestDTO request);
    CheckinResultDTO simulateCheckin(Long memberId);

    FingerprintStatusDTO getMyStatus(String authEmail);
    void deleteFingerprint(Long userId, String authEmail);
}

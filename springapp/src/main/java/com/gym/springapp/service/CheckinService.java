package com.gym.springapp.service;

import com.gym.springapp.dto.CheckinResponseDTO;
import com.gym.springapp.dto.FaceCheckinRequestDTO;
import com.gym.springapp.dto.PasskeyAssertionVerifyDTO;
import com.gym.springapp.model.AttendanceMethod;
import com.gym.springapp.model.Member;

public interface CheckinService {
    CheckinResponseDTO checkinFace(FaceCheckinRequestDTO request);
    CheckinResponseDTO checkinPasskey(PasskeyAssertionVerifyDTO request, String authEmailOrNull);
    CheckinResponseDTO checkinManual(Long memberId);
    CheckinResponseDTO processCheckin(Member member, AttendanceMethod method);
}

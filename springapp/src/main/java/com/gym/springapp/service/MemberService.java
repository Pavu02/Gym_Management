package com.gym.springapp.service;

import com.gym.springapp.dto.MemberRequestDTO;
import com.gym.springapp.dto.MemberResponseDTO;

import java.util.List;

public interface MemberService {
    MemberResponseDTO addMember(MemberRequestDTO request);
    List<MemberResponseDTO> getAllMembers();
    MemberResponseDTO getMemberById(Long id);
    MemberResponseDTO updateMember(Long id, MemberRequestDTO request);
    void deleteMember(Long id);
    MemberResponseDTO assignTrainer(Long memberId, Long trainerId);
    MemberResponseDTO getMyProfile(String email);
    List<MemberResponseDTO> getMembersOfTrainer(String email);
}

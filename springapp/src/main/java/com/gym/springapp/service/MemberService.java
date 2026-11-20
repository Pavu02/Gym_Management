package com.gym.springapp.service;

import com.gym.springapp.dto.MemberProfileUpdateDTO;
import com.gym.springapp.dto.MemberRequestDTO;
import com.gym.springapp.dto.MemberResponseDTO;
import com.gym.springapp.dto.RenewRequestDTO;
import com.gym.springapp.model.Member;

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

    MemberResponseDTO toDTO(Member member);
    MemberResponseDTO updateMyProfile(String email, MemberProfileUpdateDTO request);
    MemberResponseDTO changeTrainer(String email, Long trainerId);
    MemberResponseDTO removeTrainer(String email);
    MemberResponseDTO renewMembership(String email, RenewRequestDTO request);
}

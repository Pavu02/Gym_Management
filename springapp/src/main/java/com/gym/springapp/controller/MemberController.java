package com.gym.springapp.controller;

import com.gym.springapp.dto.MemberProfileUpdateDTO;
import com.gym.springapp.dto.MemberRequestDTO;
import com.gym.springapp.dto.MemberResponseDTO;
import com.gym.springapp.dto.RenewRequestDTO;
import com.gym.springapp.service.MemberService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MemberResponseDTO>> getAllMembers() {
        return ResponseEntity.ok(memberService.getAllMembers());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MemberResponseDTO> getMemberById(@PathVariable Long id) {
        return ResponseEntity.ok(memberService.getMemberById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MemberResponseDTO> addMember(@RequestBody MemberRequestDTO request) {
        MemberResponseDTO created = memberService.addMember(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MemberResponseDTO> updateMember(@PathVariable Long id, @RequestBody MemberRequestDTO request) {
        MemberResponseDTO updated = memberService.updateMember(id, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteMember(@PathVariable Long id) {
        memberService.deleteMember(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/assign-trainer/{trainerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MemberResponseDTO> assignTrainer(@PathVariable Long id, @PathVariable Long trainerId) {
        MemberResponseDTO updated = memberService.assignTrainer(id, trainerId);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<MemberResponseDTO> getMyProfile(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(memberService.getMyProfile(email));
    }

    @PutMapping("/me/profile")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<MemberResponseDTO> updateMyProfile(Authentication authentication,
                                                             @RequestBody MemberProfileUpdateDTO request) {
        String email = authentication.getName();
        return ResponseEntity.ok(memberService.updateMyProfile(email, request));
    }

    @PutMapping("/me/trainer/{id}")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<MemberResponseDTO> changeMyTrainer(Authentication authentication,
                                                            @PathVariable Long id) {
        String email = authentication.getName();
        return ResponseEntity.ok(memberService.changeTrainer(email, id));
    }

    @PutMapping("/me/trainer/remove")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<MemberResponseDTO> removeMyTrainer(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(memberService.removeTrainer(email));
    }

    @PostMapping("/me/renew")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<MemberResponseDTO> renewMembership(Authentication authentication,
                                                             @RequestBody RenewRequestDTO request) {
        String email = authentication.getName();
        return ResponseEntity.ok(memberService.renewMembership(email, request));
    }
}

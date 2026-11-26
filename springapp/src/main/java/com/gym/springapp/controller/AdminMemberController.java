package com.gym.springapp.controller;

import com.gym.springapp.dto.MemberDeactivateRequestDTO;
import com.gym.springapp.dto.MemberStatusActionResponseDTO;
import com.gym.springapp.model.MemberAuditLog;
import com.gym.springapp.service.MemberAuditService;
import com.gym.springapp.service.MemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/members")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMemberController {

    private final MemberService memberService;
    private final MemberAuditService memberAuditService;

    public AdminMemberController(MemberService memberService, MemberAuditService memberAuditService) {
        this.memberService = memberService;
        this.memberAuditService = memberAuditService;
    }

    @PatchMapping("/{memberId}/deactivate")
    public ResponseEntity<MemberStatusActionResponseDTO> deactivateMember(
            @PathVariable Long memberId,
            @RequestBody(required = false) MemberDeactivateRequestDTO request,
            Authentication authentication) {
        String reason = request != null ? request.reason() : null;
        Long expectedVersion = request != null ? request.expectedVersion() : null;
        return ResponseEntity
                .ok(memberService.deactivateMember(memberId, authentication.getName(), reason, expectedVersion));
    }

    @PatchMapping("/{memberId}/reactivate")
    public ResponseEntity<MemberStatusActionResponseDTO> reactivateMember(
            @PathVariable Long memberId,
            @RequestBody(required = false) MemberDeactivateRequestDTO request,
            Authentication authentication) {
        Long expectedVersion = request != null ? request.expectedVersion() : null;
        return ResponseEntity.ok(memberService.reactivateMember(memberId, authentication.getName(), expectedVersion));
    }

    @GetMapping("/{memberId}/audit-logs")
    public ResponseEntity<List<MemberAuditLog>> getMemberAuditLogs(@PathVariable Long memberId) {
        return ResponseEntity.ok(memberAuditService.getAuditTrail(memberId));
    }
}

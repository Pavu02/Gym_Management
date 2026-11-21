package com.gym.springapp.controller;

import com.gym.springapp.dto.*;
import com.gym.springapp.model.Member;
import com.gym.springapp.service.PasskeyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class PasskeyController {

    private final PasskeyService passkeyService;

    public PasskeyController(PasskeyService passkeyService) {
        this.passkeyService = passkeyService;
    }

    @PostMapping("/api/member/passkey/register/options")
    @PreAuthorize("hasAnyRole('MEMBER', 'ADMIN')")
    public ResponseEntity<PasskeyRegistrationOptionsDTO> startRegistration(Authentication authentication) {
        String authEmail = authentication.getName();
        PasskeyRegistrationOptionsDTO options = passkeyService.startRegistration(authEmail);
        return ResponseEntity.ok(options);
    }

    @PostMapping("/api/member/passkey/register/verify")
    @PreAuthorize("hasAnyRole('MEMBER', 'ADMIN')")
    public ResponseEntity<WebAuthnDeviceDTO> finishRegistration(
            @RequestBody PasskeyRegisterVerifyDTO verifyDTO,
            Authentication authentication) {
        String authEmail = authentication.getName();
        WebAuthnDeviceDTO device = passkeyService.finishRegistration(authEmail, verifyDTO);
        return ResponseEntity.ok(device);
    }

    @GetMapping("/api/member/passkey/devices")
    @PreAuthorize("hasAnyRole('MEMBER', 'ADMIN')")
    public ResponseEntity<List<WebAuthnDeviceDTO>> getDevices(Authentication authentication) {
        String authEmail = authentication.getName();
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(r -> r.equals("ROLE_ADMIN"));

        List<WebAuthnDeviceDTO> devices = passkeyService.getMemberDevices(authEmail, null, isAdmin);
        return ResponseEntity.ok(devices);
    }

    @DeleteMapping("/api/member/passkey/{credentialId}")
    @PreAuthorize("hasAnyRole('MEMBER', 'ADMIN')")
    public ResponseEntity<Void> deleteDevice(
            @PathVariable String credentialId,
            Authentication authentication) {
        String authEmail = authentication.getName();
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(r -> r.equals("ROLE_ADMIN"));

        passkeyService.deleteDevice(authEmail, credentialId, isAdmin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/checkin/passkey/options")
    public ResponseEntity<PasskeyAssertionOptionsDTO> startCheckinAssertion(
            @RequestParam(required = false) String email,
            Authentication authentication) {
        String targetEmail = (authentication != null && authentication.isAuthenticated() && !authentication.getName().equals("anonymousUser"))
                ? authentication.getName()
                : email;

        PasskeyAssertionOptionsDTO options = passkeyService.startAssertion(targetEmail);
        return ResponseEntity.ok(options);
    }

    @PostMapping("/api/checkin/passkey/verify")
    public ResponseEntity<Map<String, Object>> verifyCheckinAssertion(
            @RequestBody PasskeyAssertionVerifyDTO verifyDTO,
            Authentication authentication) {
        String authEmail = (authentication != null && authentication.isAuthenticated() && !authentication.getName().equals("anonymousUser"))
                ? authentication.getName()
                : null;

        Member member = passkeyService.finishAssertion(authEmail, verifyDTO);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Passkey assertion verified successfully",
                "memberId", member.getId(),
                "memberName", member.getUser().getName(),
                "memberEmail", member.getUser().getEmail()
        ));
    }
}

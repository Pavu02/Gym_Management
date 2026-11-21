package com.gym.springapp.controller;

import com.gym.springapp.dto.BiometricResponseDTO;
import com.gym.springapp.dto.BiometricStatusResponseDTO;
import com.gym.springapp.dto.FaceEnrollmentRequestDTO;
import com.gym.springapp.service.BiometricService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/member/biometrics")
public class BiometricController {

    private final BiometricService biometricService;

    public BiometricController(BiometricService biometricService) {
        this.biometricService = biometricService;
    }

    @PostMapping("/face")
    @PreAuthorize("hasAnyRole('MEMBER', 'ADMIN')")
    public ResponseEntity<BiometricResponseDTO> enrollFace(
            @Valid @RequestBody FaceEnrollmentRequestDTO request,
            Authentication authentication) {
        String authEmail = authentication.getName();
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_ADMIN"));

        BiometricResponseDTO response = biometricService.enrollFace(request, authEmail, isAdmin);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<BiometricStatusResponseDTO> getMyBiometricStatus(Authentication authentication) {
        String authEmail = authentication.getName();
        BiometricStatusResponseDTO status = biometricService.getBiometricStatus(null, authEmail, false);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/status/{memberId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BiometricStatusResponseDTO> getMemberBiometricStatus(
            @PathVariable Long memberId,
            Authentication authentication) {
        String authEmail = authentication.getName();
        BiometricStatusResponseDTO status = biometricService.getBiometricStatus(memberId, authEmail, true);
        return ResponseEntity.ok(status);
    }

    @DeleteMapping
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<Void> deleteMyBiometrics(Authentication authentication) {
        String authEmail = authentication.getName();
        biometricService.deleteBiometrics(null, authEmail, false);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{memberId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteMemberBiometrics(
            @PathVariable Long memberId,
            Authentication authentication) {
        String authEmail = authentication.getName();
        biometricService.deleteBiometrics(memberId, authEmail, true);
        return ResponseEntity.noContent().build();
    }
}

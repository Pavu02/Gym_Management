package com.gym.springapp.controller;

import com.gym.springapp.dto.FingerprintDeviceDTO;
import com.gym.springapp.dto.RegisterFinishRequestDTO;
import com.gym.springapp.dto.RegisterStartResponseDTO;
import com.gym.springapp.service.FingerprintService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fingerprint")
public class FingerprintController {

    private final FingerprintService fingerprintService;

    public FingerprintController(FingerprintService fingerprintService) {
        this.fingerprintService = fingerprintService;
    }

    @PostMapping("/register/start")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<RegisterStartResponseDTO> startRegistration(Authentication authentication) {
        String authEmail = authentication.getName();
        RegisterStartResponseDTO response = fingerprintService.startRegistration(authEmail);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register/finish")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<FingerprintDeviceDTO> finishRegistration(
            @RequestBody RegisterFinishRequestDTO request,
            Authentication authentication) {
        String authEmail = authentication.getName();
        FingerprintDeviceDTO device = fingerprintService.finishRegistration(authEmail, request);
        return ResponseEntity.ok(device);
    }

    @GetMapping("/me/devices")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<List<FingerprintDeviceDTO>> getMyDevices(Authentication authentication) {
        String authEmail = authentication.getName();
        List<FingerprintDeviceDTO> devices = fingerprintService.getMemberDevices(authEmail);
        return ResponseEntity.ok(devices);
    }

    @DeleteMapping("/me/devices/{id}")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<Map<String, String>> deleteMyDevice(
            @PathVariable Long id,
            Authentication authentication) {
        String authEmail = authentication.getName();
        fingerprintService.deleteMyDevice(authEmail, id);
        return ResponseEntity.ok(Map.of("message", "Device removed successfully"));
    }

    @GetMapping("/member/{memberId}/devices")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<FingerprintDeviceDTO>> getMemberDevices(@PathVariable Long memberId) {
        List<FingerprintDeviceDTO> devices = fingerprintService.getDevicesByMemberId(memberId);
        return ResponseEntity.ok(devices);
    }

    @DeleteMapping("/devices/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> adminDeleteDevice(@PathVariable Long id) {
        fingerprintService.adminDeleteDevice(id);
        return ResponseEntity.ok(Map.of("message", "Device revoked successfully"));
    }

    @PostMapping("/checkin/start")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<com.gym.springapp.dto.CheckinStartResponseDTO> startCheckin(
            @RequestBody com.gym.springapp.dto.CheckinStartRequestDTO request,
            Authentication authentication) {
        String authEmail = authentication.getName();
        com.gym.springapp.dto.CheckinStartResponseDTO response = fingerprintService.startCheckin(authEmail, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/checkin/finish")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<com.gym.springapp.dto.CheckinResultDTO> finishCheckin(
            @RequestBody com.gym.springapp.dto.CheckinFinishRequestDTO request,
            Authentication authentication) {
        String authEmail = authentication.getName();
        com.gym.springapp.dto.CheckinResultDTO response = fingerprintService.finishCheckin(authEmail, request);
        return ResponseEntity.ok(response);
    }
}

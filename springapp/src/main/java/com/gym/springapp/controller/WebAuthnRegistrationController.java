package com.gym.springapp.controller;

import com.gym.springapp.dto.RegisteredDeviceDTO;
import com.gym.springapp.dto.WebAuthnDtoBundle.RegisterOptionsRequest;
import com.gym.springapp.dto.WebAuthnDtoBundle.RegisterOptionsResponse;
import com.gym.springapp.dto.WebAuthnDtoBundle.RegisterVerifyRequest;
import com.gym.springapp.dto.WebAuthnDtoBundle.RenameDeviceRequest;
import com.gym.springapp.dto.WebAuthnDtoBundle.RevokeAllDevicesRequest;
import com.gym.springapp.service.BiometricCredentialService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/webauthn")
@PreAuthorize("hasRole('MEMBER')")
public class WebAuthnRegistrationController {

    private final BiometricCredentialService credentialService;

    public WebAuthnRegistrationController(BiometricCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @PostMapping("/register/options")
    public ResponseEntity<RegisterOptionsResponse> startRegistration(
            @RequestBody(required = false) RegisterOptionsRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(credentialService.startRegistration(authentication.getName(), request));
    }

    @PostMapping("/register/verify")
    public ResponseEntity<RegisteredDeviceDTO> finishRegistration(
            @RequestBody RegisterVerifyRequest request,
            Authentication authentication) {
        RegisteredDeviceDTO saved = credentialService.finishRegistration(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/devices")
    public ResponseEntity<List<RegisteredDeviceDTO>> listMyDevices(Authentication authentication) {
        return ResponseEntity.ok(credentialService.getMyDevices(authentication.getName()));
    }

    @PatchMapping("/devices/{deviceId}/rename")
    public ResponseEntity<RegisteredDeviceDTO> renameDevice(
            @PathVariable Long deviceId,
            @RequestBody RenameDeviceRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(credentialService.renameMyDevice(
                authentication.getName(), deviceId, request != null ? request.deviceName() : null));
    }

    @DeleteMapping("/devices/{deviceId}")
    public ResponseEntity<Map<String, String>> revokeDevice(
            @PathVariable Long deviceId,
            Authentication authentication) {
        credentialService.revokeMyDevice(authentication.getName(), deviceId);
        return ResponseEntity.ok(Map.of("message", "Device biometric access revoked successfully."));
    }

    @PostMapping("/devices/revoke-all")
    public ResponseEntity<Map<String, Object>> revokeAllDevices(
            @RequestBody RevokeAllDevicesRequest request,
            Authentication authentication) {
        int count = credentialService.revokeAllMyDevices(
                authentication.getName(), request != null ? request.currentPassword() : null);
        return ResponseEntity.ok(Map.of("revokedCount", count, "message", "All registered devices revoked."));
    }
}

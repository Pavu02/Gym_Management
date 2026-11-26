package com.gym.springapp.controller;

import com.gym.springapp.dto.WebAuthnDtoBundle.*;
import com.gym.springapp.service.QrCheckInSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/qr-checkin")
public class QrCheckInController {

    private final QrCheckInSessionService sessionService;

    public QrCheckInController(QrCheckInSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/sessions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<QrSessionResponse> createSession(
            @RequestBody(required = false) QrSessionCreateRequest request,
            Authentication authentication) {
        QrSessionResponse response = sessionService.createSession(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/sessions/{sessionCode}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CheckInWebSocketEvent> getSessionStatus(
            @PathVariable String sessionCode,
            Authentication authentication) {
        return ResponseEntity.ok(sessionService.getSessionStatusForAdmin(authentication.getName(), sessionCode));
    }

    @DeleteMapping("/sessions/{sessionCode}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> cancelSession(
            @PathVariable String sessionCode,
            Authentication authentication) {
        sessionService.cancelSession(authentication.getName(), sessionCode);
        return ResponseEntity.ok(Map.of("message", "QR check-in session cancelled."));
    }

    @PostMapping("/manual-checkin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CheckInVerificationResponse> manualFallbackCheckIn(
            @RequestBody AdminManualCheckInRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(sessionService.adminManualCheckIn(authentication.getName(), request));
    }

    @PostMapping("/sessions/validate")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<QrSessionValidateResponse> validateSession(
            @RequestBody QrSessionValidateRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        return ResponseEntity.ok(sessionService.validateSessionForMember(authentication.getName(), request, clientIp));
    }

    @GetMapping("/sessions/member-status")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<CheckInVerificationResponse> confirmByIdempotencyKey(
            @RequestParam String idempotencyKey,
            Authentication authentication) {
        return ResponseEntity.ok(sessionService.lookupByIdempotencyKey(authentication.getName(), idempotencyKey));
    }

    private String extractClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}

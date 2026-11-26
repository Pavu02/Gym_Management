package com.gym.springapp.controller;

import com.gym.springapp.dto.WebAuthnDtoBundle.AuthOptionsRequest;
import com.gym.springapp.dto.WebAuthnDtoBundle.AuthOptionsResponse;
import com.gym.springapp.dto.WebAuthnDtoBundle.AuthVerifyRequest;
import com.gym.springapp.dto.WebAuthnDtoBundle.CheckInVerificationResponse;
import com.gym.springapp.service.QrCheckInSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webauthn/authenticate")
@PreAuthorize("hasRole('MEMBER')")
public class WebAuthnAuthenticationController {

    private final QrCheckInSessionService sessionService;

    public WebAuthnAuthenticationController(QrCheckInSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/options")
    public ResponseEntity<AuthOptionsResponse> getAuthOptions(
            @RequestBody AuthOptionsRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(sessionService.startWebAuthnAssertion(authentication.getName(), request));
    }

    @PostMapping("/verify")
    public ResponseEntity<CheckInVerificationResponse> verifyCheckIn(
            @RequestBody AuthVerifyRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        return ResponseEntity.ok(sessionService.verifyWebAuthnAndCheckIn(authentication.getName(), request, clientIp));
    }

    private String extractClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}

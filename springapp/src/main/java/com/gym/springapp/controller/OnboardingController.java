package com.gym.springapp.controller;

import com.gym.springapp.dto.*;
import com.gym.springapp.model.Plan;
import com.gym.springapp.model.Trainer;
import com.gym.springapp.service.OnboardingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/onboarding")
@PreAuthorize("hasRole('MEMBER')")
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @GetMapping("/status")
    public ResponseEntity<OnboardingStatusDTO> getStatus(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(onboardingService.getStatus(email));
    }

    @GetMapping("/plans")
    public ResponseEntity<List<Plan>> getPlans() {
        return ResponseEntity.ok(onboardingService.getPlansSortedByPrice());
    }

    @GetMapping("/trainers")
    public ResponseEntity<List<Trainer>> getTrainers() {
        return ResponseEntity.ok(onboardingService.getAllTrainers());
    }

    @PostMapping("/enroll")
    public ResponseEntity<Map<String, String>> enroll(Authentication authentication,
                                                     @RequestBody EnrollRequestDTO request) {
        String email = authentication.getName();
        onboardingService.enroll(email, request);
        return ResponseEntity.ok(Map.of("message", "Enrollment updated successfully"));
    }

    @PutMapping("/profile")
    public ResponseEntity<Map<String, String>> setupProfile(Authentication authentication,
                                                            @Valid @RequestBody ProfileSetupDTO request) {
        String email = authentication.getName();
        onboardingService.setupProfile(email, request);
        return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));
    }

    @GetMapping("/summary")
    public ResponseEntity<OnboardingSummaryDTO> getSummary(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(onboardingService.getSummary(email));
    }

    @PostMapping("/payment")
    public ResponseEntity<MemberResponseDTO> completePayment(Authentication authentication,
                                                             @RequestBody OnboardingPaymentDTO request) {
        String email = authentication.getName();
        MemberResponseDTO response = onboardingService.completePayment(email, request);
        return ResponseEntity.ok(response);
    }
}

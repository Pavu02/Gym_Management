package com.gym.springapp.controller;

import com.gym.springapp.dto.FaceProfileDescriptorDTO;
import com.gym.springapp.model.FaceProfile;
import com.gym.springapp.service.FaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/face")
public class FaceController {

    private final FaceService faceService;

    public FaceController(FaceService faceService) {
        this.faceService = faceService;
    }

    @PostMapping("/enroll/{userId}")
    @PreAuthorize("hasRole('ADMIN') or hasAnyRole('MEMBER', 'TRAINER')")
    public ResponseEntity<Map<String, Object>> enrollFace(
            @PathVariable Long userId,
            @RequestBody List<Double> descriptor,
            Authentication authentication) {

        String authEmail = authentication.getName();
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(r -> r.equals("ROLE_ADMIN"));

        FaceProfile saved = faceService.enrollFace(userId, descriptor, authEmail, isAdmin);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Face enrolled successfully",
                "userId", saved.getUserId(),
                "role", saved.getRole(),
                "updatedAt", saved.getUpdatedAt()
        ));
    }

    @GetMapping("/descriptors")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<FaceProfileDescriptorDTO>> getAllDescriptors() {
        return ResponseEntity.ok(faceService.getAllDescriptors());
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteProfile(@PathVariable Long userId) {
        faceService.deleteProfile(userId);
        return ResponseEntity.ok(Map.of("message", "Face profile removed successfully"));
    }
}

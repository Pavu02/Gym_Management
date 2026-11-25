package com.gym.springapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "fingerprint_profiles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_fingerprint_profile_user", columnNames = { "user_id" })
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FingerprintProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "role", nullable = false, length = 20)
    private String role; // "MEMBER" or "TRAINER"

    @Column(name = "fingerprint_hash", nullable = false, length = 128)
    private String fingerprintHash; // Unique cryptographic biometric template hash

    @Column(name = "minutiae_data", columnDefinition = "TEXT")
    private String minutiaeData; // JSON representation of ridge minutiae points

    @Column(name = "quality_score")
    private Integer qualityScore; // e.g. 98

    @Column(name = "device_name", length = 100)
    private String deviceName; // e.g. "Optical Biometric Sensor"

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

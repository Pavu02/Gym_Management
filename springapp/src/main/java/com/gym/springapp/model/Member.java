package com.gym.springapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "members")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "plan_id", nullable = true)
    private Plan plan;

    @ManyToOne
    @JoinColumn(name = "trainer_id", nullable = true)
    private Trainer trainer;

    private LocalDate joinDate;

    private LocalDate expiryDate;

    private String status;

    private String gender;

    private LocalDate dateOfBirth;

    private double heightCm;

    private double weightKg;

    private String fitnessGoal;

    private String medicalConditions;

    @Column(columnDefinition = "TEXT")
    private String specialRequirements;

    @Column(nullable = false)
    private boolean emailRemindersEnabled = true;

    private String emergencyContactName;

    private String emergencyContactPhone;

    private boolean onboardingCompleted = false;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(name = "webauthn_user_handle", unique = true, length = 128)
    private String webAuthnUserHandle;

    @Column(name = "gym_id", length = 64)
    private String gymId = "BEASTX-01";

    @Column(name = "branch_id", length = 64)
    private String branchId = "MAIN";

    @Column(name = "face_enabled")
    private Boolean faceEnabled = Boolean.TRUE;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    @Column(name = "deactivated_by")
    private Long deactivatedBy;

    @Column(name = "deactivation_reason", length = 500)
    private String deactivationReason;

    @Version
    @Column(name = "version")
    private Long version = 0L;

    public boolean isFaceEnabled() {
        return !Boolean.FALSE.equals(faceEnabled);
    }

    public void setFaceEnabled(boolean faceEnabled) {
        this.faceEnabled = faceEnabled;
    }
}

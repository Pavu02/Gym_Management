package com.gym.springapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    private String emergencyContactName;

    private String emergencyContactPhone;

    private boolean onboardingCompleted = false;
}

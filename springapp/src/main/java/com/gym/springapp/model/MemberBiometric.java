package com.gym.springapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "member_biometrics")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberBiometric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    @Lob
    @Column(name = "face_descriptor_encrypted", columnDefinition = "LONGTEXT", nullable = false)
    private String faceDescriptorEncrypted;

    @Column(name = "face_enrolled_at", nullable = false)
    private LocalDateTime faceEnrolledAt;

    @Column(name = "consent_at", nullable = false)
    private LocalDateTime consentAt;
}

package com.gym.springapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "face_profiles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_face_profile_user", columnNames = { "user_id" })
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FaceProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "role", nullable = false, length = 20)
    private String role; // "MEMBER" or "TRAINER"

    @Column(name = "descriptor", columnDefinition = "TEXT", nullable = false)
    private String descriptor; // JSON array of 128 floats

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

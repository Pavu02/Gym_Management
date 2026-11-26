package com.gym.springapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "webauthn_challenges", uniqueConstraints = {
        @UniqueConstraint(name = "uk_webauthn_challenge_id", columnNames = "challenge_id")
}, indexes = {
        @Index(name = "idx_wc_member_op", columnList = "member_id, operation, consumed"),
        @Index(name = "idx_wc_expires_at", columnList = "expires_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebAuthnChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "challenge_id", nullable = false, unique = true, length = 64)
    private String challengeId;

    @Column(name = "challenge_value", nullable = false, length = 128)
    private String challengeValue;

    @Column(name = "operation", nullable = false, length = 32)
    private String operation;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "qr_session_id")
    private Long qrSessionId;

    @Lob
    @Column(name = "options_json", nullable = false, columnDefinition = "TEXT")
    private String optionsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed", nullable = false)
    private boolean consumed = false;

    @Column(name = "consumed_at")
    private Instant consumedAt;
}

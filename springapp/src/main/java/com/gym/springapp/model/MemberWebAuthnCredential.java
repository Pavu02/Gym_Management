package com.gym.springapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "member_webauthn_credentials", uniqueConstraints = {
        @UniqueConstraint(name = "uk_webauthn_credential_id", columnNames = "credential_id")
}, indexes = {
        @Index(name = "idx_mwc_member_active", columnList = "member_id, active"),
        @Index(name = "idx_mwc_user_handle", columnList = "user_handle")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberWebAuthnCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "credential_id", nullable = false, unique = true, length = 512)
    private String credentialId;

    @Lob
    @Column(name = "public_key_cose", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] publicKeyCose;

    @Column(name = "user_handle", nullable = false, length = 128)
    private String userHandle;

    @Column(name = "signature_count", nullable = false)
    private long signatureCount = 0L;

    @Column(name = "device_name", nullable = false, length = 120)
    private String deviceName;

    @Column(name = "device_type", nullable = false, length = 60)
    private String deviceType = "UNKNOWN";

    @Column(name = "transports", length = 255)
    private String transports;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}

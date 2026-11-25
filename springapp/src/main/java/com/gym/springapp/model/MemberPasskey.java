package com.gym.springapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "member_passkeys", indexes = {
        @Index(name = "idx_member_passkeys_cred_id", columnList = "credential_id"),
        @Index(name = "idx_member_passkeys_member", columnList = "member_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberPasskey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "credential_id", nullable = false, unique = true, length = 512)
    private String credentialId; // base64url String

    @Column(name = "user_handle", length = 255)
    private String userHandle;

    @Lob
    @Column(name = "public_key_cose", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] publicKeyCose;

    @Column(name = "sign_count", nullable = false)
    private long signCount = 0;

    @Column(name = "device_name", length = 100)
    private String deviceName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
}

package com.gym.springapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "qr_checkin_sessions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_qr_token_hash", columnNames = "token_hash"),
        @UniqueConstraint(name = "uk_qr_session_code", columnNames = "session_code")
}, indexes = {
        @Index(name = "idx_qr_admin_status", columnList = "created_by_admin_id, status"),
        @Index(name = "idx_qr_expires_at", columnList = "expires_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QrCheckInSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_code", nullable = false, unique = true, length = 64)
    private String sessionCode;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "gym_id", nullable = false, length = 64)
    private String gymId = "BEASTX-01";

    @Column(name = "branch_id", nullable = false, length = 64)
    private String branchId = "MAIN";

    @Column(name = "created_by_admin_id", nullable = false)
    private Long createdByAdminId;

    @Column(name = "member_id")
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private QrSessionStatus status = QrSessionStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "verification_method", length = 60)
    private String verificationMethod;

    @Column(name = "risk_result", length = 120)
    private String riskResult;

    @Column(name = "idempotency_key", unique = true, length = 128)
    private String idempotencyKey;

    @Version
    private Long version;

    public boolean isTerminal() {
        return status == QrSessionStatus.COMPLETED
                || status == QrSessionStatus.CONSUMED
                || status == QrSessionStatus.EXPIRED
                || status == QrSessionStatus.CANCELLED;
    }

    public boolean isClaimableForVerification() {
        return status == QrSessionStatus.PENDING
                || status == QrSessionStatus.PHONE_CONNECTED
                || status == QrSessionStatus.AWAITING_USER_VERIFICATION
                || status == QrSessionStatus.VERIFYING;
    }
}

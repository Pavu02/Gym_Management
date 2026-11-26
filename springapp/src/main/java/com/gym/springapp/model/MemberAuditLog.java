package com.gym.springapp.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "member_audit_logs", indexes = {
        @Index(name = "idx_mal_member_created", columnList = "member_id, created_at"),
        @Index(name = "idx_mal_action_result", columnList = "action, result")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "action", nullable = false, length = 60)
    private String action; // MEMBER_DEACTIVATED, MEMBER_REACTIVATED, CHECKIN_DENIED

    @Column(name = "performed_by")
    private Long performedBy;

    @Column(name = "performed_by_email", length = 160)
    private String performedByEmail;

    @Column(name = "method", length = 40)
    private String method; // FACE, QR, BIOMETRIC, MANUAL, LOGIN, JWT

    @Column(name = "result", nullable = false, length = 40)
    private String result; // ADMIN_ACTION, DENIED, SUCCESS

    @Column(name = "reason", length = 500)
    private String reason; // MEMBER_DEACTIVATED or admin note

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

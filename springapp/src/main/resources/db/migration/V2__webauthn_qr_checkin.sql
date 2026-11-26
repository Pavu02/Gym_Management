-- Migration V2: Cross-Device WebAuthn Biometric Credentials & Single-Use QR Check-In Sessions

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS gym_id VARCHAR(64) DEFAULT 'BEASTX-01',
    ADD COLUMN IF NOT EXISTS branch_id VARCHAR(64) DEFAULT 'MAIN';

ALTER TABLE members
    ADD COLUMN IF NOT EXISTS webauthn_user_handle VARCHAR(128) NULL UNIQUE,
    ADD COLUMN IF NOT EXISTS gym_id VARCHAR(64) DEFAULT 'BEASTX-01',
    ADD COLUMN IF NOT EXISTS branch_id VARCHAR(64) DEFAULT 'MAIN';

CREATE TABLE IF NOT EXISTS member_webauthn_credentials (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    credential_id VARCHAR(512) NOT NULL,
    public_key_cose LONGBLOB NOT NULL,
    user_handle VARCHAR(128) NOT NULL,
    signature_count BIGINT NOT NULL DEFAULT 0,
    device_name VARCHAR(120) NOT NULL,
    device_type VARCHAR(60) NOT NULL DEFAULT 'UNKNOWN',
    transports VARCHAR(255) NULL,
    registered_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_used_at TIMESTAMP(6) NULL,
    active BIT(1) NOT NULL DEFAULT b'1',
    revoked_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_webauthn_credential_id UNIQUE (credential_id),
    CONSTRAINT fk_mwc_member FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE,
    INDEX idx_mwc_member_active (member_id, active),
    INDEX idx_mwc_user_handle (user_handle)
);

CREATE TABLE IF NOT EXISTS qr_checkin_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_code VARCHAR(64) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    gym_id VARCHAR(64) NOT NULL DEFAULT 'BEASTX-01',
    branch_id VARCHAR(64) NOT NULL DEFAULT 'MAIN',
    created_by_admin_id BIGINT NOT NULL,
    member_id BIGINT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at TIMESTAMP(6) NOT NULL,
    consumed_at TIMESTAMP(6) NULL,
    failure_reason VARCHAR(255) NULL,
    verification_method VARCHAR(60) NULL,
    risk_result VARCHAR(120) NULL,
    idempotency_key VARCHAR(128) NULL,
    version BIGINT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_qr_token_hash UNIQUE (token_hash),
    CONSTRAINT uk_qr_session_code UNIQUE (session_code),
    CONSTRAINT uk_qr_idempotency_key UNIQUE (idempotency_key),
    INDEX idx_qr_admin_status (created_by_admin_id, status),
    INDEX idx_qr_expires_at (expires_at)
);

CREATE TABLE IF NOT EXISTS webauthn_challenges (
    id BIGINT NOT NULL AUTO_INCREMENT,
    challenge_id VARCHAR(64) NOT NULL,
    challenge_value VARCHAR(128) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    member_id BIGINT NOT NULL,
    qr_session_id BIGINT NULL,
    options_json TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at TIMESTAMP(6) NOT NULL,
    consumed BIT(1) NOT NULL DEFAULT b'0',
    consumed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_webauthn_challenge_id UNIQUE (challenge_id),
    INDEX idx_wc_member_op (member_id, operation, consumed),
    INDEX idx_wc_expires_at (expires_at)
);

-- Allow re-entry after checkout while guaranteeing at most one attendance row per QR session
ALTER TABLE face_attendance
    MODIFY COLUMN method VARCHAR(40) NOT NULL;

ALTER TABLE attendance
    MODIFY COLUMN method VARCHAR(40) NOT NULL,
    MODIFY COLUMN check_in_method VARCHAR(40) NULL,
    ADD COLUMN IF NOT EXISTS gym_id VARCHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS branch_id VARCHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS check_in_timestamp TIMESTAMP(6) NULL,
    ADD COLUMN IF NOT EXISTS verification_result VARCHAR(40) NULL,
    ADD COLUMN IF NOT EXISTS verification_method_label VARCHAR(60) NULL,
    ADD COLUMN IF NOT EXISTS risk_result VARCHAR(120) NULL,
    ADD COLUMN IF NOT EXISTS qr_session_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS device_credential_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128) NULL,
    ADD COLUMN IF NOT EXISTS fallback_reason VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP(6) NULL;

-- Drop single-visit-per-day constraint if upgrading from V1 schema so checkout -> re-check-in is supported
-- Note: MySQL 8+ syntax: DROP INDEX uk_member_attendance_date ON attendance;
-- Add per-session idempotency constraints:
ALTER TABLE attendance
    ADD CONSTRAINT uk_attendance_member_qr_session UNIQUE (member_id, qr_session_id),
    ADD CONSTRAINT uk_attendance_idempotency UNIQUE (idempotency_key);

-- Migration V3: Member Deactivation, Face Control, Attendance Checkout Reason, and Audit Log

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS token_version BIGINT DEFAULT 0;

ALTER TABLE members
    ADD COLUMN IF NOT EXISTS face_enabled BIT(1) DEFAULT b'1',
    ADD COLUMN IF NOT EXISTS deactivated_at TIMESTAMP(6) NULL,
    ADD COLUMN IF NOT EXISTS deactivated_by BIGINT NULL,
    ADD COLUMN IF NOT EXISTS deactivation_reason VARCHAR(500) NULL,
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

ALTER TABLE face_profiles
    ADD COLUMN IF NOT EXISTS enabled BIT(1) DEFAULT b'1',
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS disabled_at TIMESTAMP(6) NULL;

ALTER TABLE member_biometrics
    ADD COLUMN IF NOT EXISTS enabled BIT(1) DEFAULT b'1',
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS disabled_at TIMESTAMP(6) NULL;

ALTER TABLE member_passkeys
    ADD COLUMN IF NOT EXISTS active BIT(1) DEFAULT b'1',
    ADD COLUMN IF NOT EXISTS revoked_at DATETIME(6) NULL;

ALTER TABLE webauthn_credentials
    ADD COLUMN IF NOT EXISTS active BIT(1) DEFAULT b'1',
    ADD COLUMN IF NOT EXISTS revoked_at DATETIME(6) NULL;

ALTER TABLE fingerprint_profiles
    ADD COLUMN IF NOT EXISTS enabled BIT(1) DEFAULT b'1',
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS disabled_at DATETIME(6) NULL;

ALTER TABLE attendance
    ADD COLUMN IF NOT EXISTS checkout_reason VARCHAR(80) NULL,
    ADD COLUMN IF NOT EXISTS checked_out_by BIGINT NULL,
    ADD COLUMN IF NOT EXISTS check_out_timestamp TIMESTAMP(6) NULL;

CREATE TABLE IF NOT EXISTS member_audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    action VARCHAR(60) NOT NULL,
    performed_by BIGINT NULL,
    performed_by_email VARCHAR(160) NULL,
    method VARCHAR(40) NULL,
    result VARCHAR(40) NOT NULL,
    reason VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_mal_member_created (member_id, created_at),
    INDEX idx_mal_action_result (action, result)
);


-- V004__create_audit_and_identity_tables.sql

-- ── Audit log — immutable, append-only ────────────────────────────────────
CREATE TABLE banking.audit_log (
    id           VARCHAR(36)  NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    actor        VARCHAR(100) NOT NULL,   -- Keycloak sub or 'SYSTEM'
    payload      TEXT         NOT NULL,   -- JSON serialisation of the domain event
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_audit_log PRIMARY KEY (id)
);

CREATE INDEX idx_audit_aggregate_id ON banking.audit_log (aggregate_id);
CREATE INDEX idx_audit_event_type   ON banking.audit_log (event_type);
CREATE INDEX idx_audit_actor        ON banking.audit_log (actor);
CREATE INDEX idx_audit_occurred_at  ON banking.audit_log (occurred_at DESC);

COMMENT ON TABLE banking.audit_log IS 'Immutable compliance trail. One row per domain event. Never update or delete.';

-- ── Identity — user profiles (linked to Keycloak) ─────────────────────────
CREATE TABLE banking.user_profiles (
    id              VARCHAR(36)   NOT NULL,   -- Keycloak sub (UUID)
    email           VARCHAR(320)  NOT NULL,
    full_name       VARCHAR(200)  NOT NULL,
    phone_number    VARCHAR(20),
    kyc_status      VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
    kyc_submitted_at TIMESTAMPTZ,
    kyc_reviewed_at  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    last_updated_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_user_profiles        PRIMARY KEY (id),
    CONSTRAINT uq_user_profiles_email  UNIQUE (email),
    CONSTRAINT chk_kyc_status          CHECK (kyc_status IN (
        'PENDING', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED'
    ))
);

CREATE INDEX idx_user_profiles_email      ON banking.user_profiles (email);
CREATE INDEX idx_user_profiles_kyc_status ON banking.user_profiles (kyc_status);

COMMENT ON TABLE  banking.user_profiles        IS 'User profile data. The authoritative identity source is Keycloak — this table stores banking-specific profile data only.';
COMMENT ON COLUMN banking.user_profiles.id     IS 'Keycloak sub claim — the stable UUID for this user';
COMMENT ON COLUMN banking.user_profiles.kyc_status IS 'KYC verification state machine: PENDING → SUBMITTED → UNDER_REVIEW → APPROVED/REJECTED';

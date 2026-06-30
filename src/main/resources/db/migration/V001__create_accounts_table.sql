-- V001__create_accounts_table.sql
-- Creates the banking schema and the accounts table.

CREATE SCHEMA IF NOT EXISTS banking;

CREATE TABLE banking.accounts (
    id              VARCHAR(36)    NOT NULL,
    owner_id        VARCHAR(36)    NOT NULL,
    owner_name      VARCHAR(200)   NOT NULL,
    balance         NUMERIC(19,4)  NOT NULL DEFAULT 0.0000,
    currency        CHAR(3)        NOT NULL,
    status          VARCHAR(30)    NOT NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    last_updated_at TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT chk_accounts_balance CHECK (balance >= 0),
    CONSTRAINT chk_accounts_status  CHECK (status IN (
        'ACTIVE', 'FROZEN', 'SUSPENDED', 'DORMANT', 'CLOSED'
    ))
);

CREATE INDEX idx_accounts_owner_id ON banking.accounts (owner_id);
CREATE INDEX idx_accounts_status   ON banking.accounts (status);

COMMENT ON TABLE  banking.accounts             IS 'Bank accounts — one row per account';
COMMENT ON COLUMN banking.accounts.id          IS 'Account ID — format ACC-XXXXXXXXXXXX from bank-core AccountId';
COMMENT ON COLUMN banking.accounts.owner_id    IS 'Keycloak sub (UUID) of the account owner';
COMMENT ON COLUMN banking.accounts.balance     IS 'Current balance — derived from ledger but cached here for performance';
COMMENT ON COLUMN banking.accounts.version     IS 'Optimistic lock version — incremented on every state change';

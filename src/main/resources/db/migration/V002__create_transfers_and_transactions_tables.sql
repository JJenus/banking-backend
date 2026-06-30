-- V002__create_transfers_and_transactions_tables.sql

CREATE TABLE banking.transfers (
    id                    VARCHAR(36)   NOT NULL,
    from_account_id       VARCHAR(36)   NOT NULL,
    to_account_id         VARCHAR(36)   NOT NULL,
    amount                NUMERIC(19,4) NOT NULL,
    currency              CHAR(3)       NOT NULL,
    description           VARCHAR(500),
    reference             VARCHAR(100)  NOT NULL,
    status                VARCHAR(30)   NOT NULL,
    debit_transaction_id  VARCHAR(36),
    credit_transaction_id VARCHAR(36),
    failure_reason        VARCHAR(1000),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    completed_at          TIMESTAMPTZ,

    CONSTRAINT pk_transfers           PRIMARY KEY (id),
    CONSTRAINT uq_transfers_reference UNIQUE (reference),
    CONSTRAINT chk_transfers_amount   CHECK (amount > 0),
    CONSTRAINT chk_transfers_status   CHECK (status IN (
        'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'REVERSED'
    )),
    CONSTRAINT fk_transfers_from_account
        FOREIGN KEY (from_account_id) REFERENCES banking.accounts (id),
    CONSTRAINT fk_transfers_to_account
        FOREIGN KEY (to_account_id)   REFERENCES banking.accounts (id)
);

CREATE INDEX idx_transfers_from_account ON banking.transfers (from_account_id);
CREATE INDEX idx_transfers_to_account   ON banking.transfers (to_account_id);
CREATE INDEX idx_transfers_status       ON banking.transfers (status);
CREATE INDEX idx_transfers_reference    ON banking.transfers (reference);
CREATE INDEX idx_transfers_created_at   ON banking.transfers (created_at DESC);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE banking.transactions (
    id              VARCHAR(36)   NOT NULL,
    account_id      VARCHAR(36)   NOT NULL,
    type            VARCHAR(30)   NOT NULL,
    amount          NUMERIC(19,4) NOT NULL,
    balance_after   NUMERIC(19,4) NOT NULL,
    description     VARCHAR(500),
    reference       VARCHAR(100),
    linked_tx_id    VARCHAR(36),
    metadata        TEXT,
    timestamp       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_transactions  PRIMARY KEY (id),
    CONSTRAINT chk_tx_type      CHECK (type IN (
        'DEPOSIT', 'WITHDRAWAL', 'TRANSFER_IN', 'TRANSFER_OUT',
        'FEE', 'INTEREST', 'REFUND', 'REVERSAL'
    )),
    CONSTRAINT fk_tx_account
        FOREIGN KEY (account_id) REFERENCES banking.accounts (id)
);

CREATE INDEX idx_transactions_account_id ON banking.transactions (account_id);
CREATE INDEX idx_transactions_type       ON banking.transactions (type);
CREATE INDEX idx_transactions_timestamp  ON banking.transactions (timestamp DESC);
CREATE INDEX idx_transactions_reference  ON banking.transactions (reference);

COMMENT ON TABLE  banking.transactions             IS 'Account statement lines — one row per debit or credit on an account';
COMMENT ON COLUMN banking.transactions.balance_after IS 'Account balance immediately after this transaction was applied';
COMMENT ON COLUMN banking.transactions.linked_tx_id  IS 'ID of the counterpart transaction for transfers (debit links to credit)';

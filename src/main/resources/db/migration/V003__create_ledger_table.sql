-- V003__create_ledger_table.sql
-- Double-entry journal entries. Append-only — no UPDATE, no DELETE ever.

CREATE TABLE banking.ledger_entries (
    id                  VARCHAR(20)   NOT NULL,   -- JNL-XXXXXXXXXXXX format
    debit_account_id    VARCHAR(36)   NOT NULL,
    credit_account_id   VARCHAR(36)   NOT NULL,
    amount              NUMERIC(19,4) NOT NULL,
    description         VARCHAR(500)  NOT NULL,
    reference           VARCHAR(100),
    source_id           VARCHAR(100),
    posted_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_ledger_entries  PRIMARY KEY (id),
    CONSTRAINT chk_ledger_amount  CHECK (amount > 0),
    CONSTRAINT chk_ledger_accounts CHECK (debit_account_id != credit_account_id)
);

CREATE INDEX idx_ledger_debit_account  ON banking.ledger_entries (debit_account_id);
CREATE INDEX idx_ledger_credit_account ON banking.ledger_entries (credit_account_id);
CREATE INDEX idx_ledger_posted_at      ON banking.ledger_entries (posted_at DESC);
CREATE INDEX idx_ledger_source_id      ON banking.ledger_entries (source_id);
CREATE INDEX idx_ledger_reference      ON banking.ledger_entries (reference);

-- Partial unique index for idempotency: one entry per (reference, source_id, debit, credit)
CREATE UNIQUE INDEX uq_ledger_idempotency
    ON banking.ledger_entries (reference, source_id, debit_account_id, credit_account_id)
    WHERE reference IS NOT NULL AND source_id IS NOT NULL;

COMMENT ON TABLE  banking.ledger_entries IS 'Double-entry journal. Every row debits one account and credits another for the same amount. NEVER UPDATE or DELETE.';
COMMENT ON COLUMN banking.ledger_entries.debit_account_id  IS 'Account debited (value flows out)';
COMMENT ON COLUMN banking.ledger_entries.credit_account_id IS 'Account credited (value flows in)';
COMMENT ON COLUMN banking.ledger_entries.amount            IS 'Always positive — sign is encoded by debit/credit side';
COMMENT ON COLUMN banking.ledger_entries.source_id         IS 'Transfer ID or Transaction ID that originated this entry';

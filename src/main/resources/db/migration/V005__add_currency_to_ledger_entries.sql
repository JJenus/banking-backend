-- V005__add_currency_to_ledger_entries.sql
-- Adds the currency column to ledger_entries, which was missing from V003.
--
-- bank-core's LedgerEntry record carries currency implicitly via its Money
-- amount field, but the JPA persistence layer needs it as an explicit column
-- to support multi-currency trial balance queries and to reconstruct a
-- bank-core LedgerEntry without re-deriving currency from the linked account.

ALTER TABLE banking.ledger_entries
    ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'NGN';

-- Drop the default after backfill — all new rows must specify currency explicitly.
ALTER TABLE banking.ledger_entries
    ALTER COLUMN currency DROP DEFAULT;

COMMENT ON COLUMN banking.ledger_entries.currency IS 'ISO 4217 currency code of the amount column';

-- Composite index to support per-currency trial balance and balance computation queries.
CREATE INDEX idx_ledger_currency ON banking.ledger_entries (currency);

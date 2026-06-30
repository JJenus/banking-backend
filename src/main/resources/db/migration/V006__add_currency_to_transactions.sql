-- V006__add_currency_to_transactions.sql
-- Adds currency column to transactions (missing from V002 scaffold).

ALTER TABLE banking.transactions
    ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'NGN';

ALTER TABLE banking.transactions
    ALTER COLUMN currency DROP DEFAULT;

CREATE INDEX idx_transactions_currency ON banking.transactions (currency);

COMMENT ON COLUMN banking.transactions.currency IS 'ISO 4217 currency code matching the amount and balance_after columns';

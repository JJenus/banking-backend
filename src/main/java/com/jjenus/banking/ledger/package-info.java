/**
 * Ledger module — double-entry journal, driven entirely by domain events.
 *
 * <p>This module has no command endpoints. Every {@code LedgerEntry} is
 * posted by {@link com.jjenus.banking.ledger.application.LedgerListener} in
 * response to a domain event from {@code accounts} (deposit, withdrawal) or
 * {@code transfers} (transfer completed, transfer reversed). There is
 * deliberately no "manually post a ledger entry" API — every entry must
 * trace back to a specific account or transfer event to preserve the audit
 * trail.
 *
 * <p>Counter-party accounting uses well-known system accounts defined in
 * {@link com.jjenus.banking.ledger.domain.SystemAccounts} (cash, fee income,
 * interest expense). These are ledger-only participants, not rows in the
 * {@code banking.accounts} table.
 *
 * <p>Public API: {@link com.jjenus.banking.ledger.LedgerQueryApi}, implemented
 * by {@link com.jjenus.banking.ledger.application.LedgerApplicationService}.
 * Used by the {@code reporting} module to generate statements and trial
 * balances.
 */
package com.jjenus.banking.ledger;

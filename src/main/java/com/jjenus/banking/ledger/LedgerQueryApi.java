package com.jjenus.banking.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Public API surface of the {@code ledger} module.
 *
 * <p>This interface is the ONLY way other modules may query ledger data.
 * No other module may inject {@code LedgerEntryJpaRepository} or
 * {@code LedgerRepositoryAdapter} directly.
 *
 * <p>Used by {@code reporting} to generate statements and trial balances.
 */
public interface LedgerQueryApi {

    /**
     * Computes the current ledger-derived balance for an account.
     *
     * @param accountId    bank-core account ID
     * @param currencyCode ISO 4217 currency code
     * @return the balance as a plain decimal string
     */
    BigDecimal computeBalance(String accountId, String currencyCode);

    /**
     * Computes the ledger-derived balance for an account as of a point in time.
     */
    BigDecimal computeBalanceAsOf(String accountId, String currencyCode, Instant asOf);

    /**
     * Returns every ledger entry involving an account, oldest first.
     */
    List<LedgerEntryView> getEntriesForAccount(String accountId);

    /**
     * Returns entries for an account within a date range — used for statement generation.
     */
    List<LedgerEntryView> getEntriesForAccountInRange(String accountId, Instant from, Instant to);

    /**
     * A read-only, module-agnostic view of a ledger entry. Decouples consumers
     * from bank-core's {@code LedgerEntry} type so the ledger module's internal
     * representation can evolve without breaking other modules' compile-time
     * dependency.
     */
    record LedgerEntryView(
        String id,
        String debitAccountId,
        String creditAccountId,
        BigDecimal amount,
        String currency,
        String description,
        String reference,
        String sourceId,
        Instant postedAt
    ) {}
}

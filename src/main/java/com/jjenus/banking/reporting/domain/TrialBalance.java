package com.jjenus.banking.reporting.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Trial balance — aggregate of all ledger-derived balances across all
 * accounts, grouped into debits and credits, as of a given point in time.
 *
 * <p>In a correctly maintained double-entry ledger, totalDebits == totalCredits.
 * The {@link #isBalanced()} method confirms this invariant. If it returns
 * false, a reconciliation alert must be raised.
 */
public record TrialBalance(
    String currency,
    Instant asOf,
    List<AccountBalanceLine> lines,
    BigDecimal totalDebits,
    BigDecimal totalCredits,
    Instant generatedAt
) {

    /**
     * Returns true if total debits equal total credits — the fundamental
     * double-entry invariant.
     */
    public boolean isBalanced() {
        return totalDebits.compareTo(totalCredits) == 0;
    }

    public record AccountBalanceLine(
        String accountId,
        String accountLabel,    // display name or system account label
        BigDecimal debitTotal,
        BigDecimal creditTotal,
        BigDecimal netBalance   // creditTotal - debitTotal
    ) {}
}

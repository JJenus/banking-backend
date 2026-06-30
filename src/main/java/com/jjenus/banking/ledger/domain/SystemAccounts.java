package com.jjenus.banking.ledger.domain;

import com.jjenus.bank.core.accounts.AccountId;

/**
 * Well-known system (internal) accounts that the ledger uses as the
 * counter-party for customer-facing movements.
 *
 * <p>Double-entry bookkeeping requires every {@code LedgerEntry} to name two
 * accounts. For a customer deposit, the customer's account is credited — but
 * something must be debited. That something is the bank's own internal cash
 * position, modeled here as a fixed, well-known {@link AccountId}.
 *
 * <p>These IDs satisfy bank-core's {@code AccountId} format
 * ({@code ACC-XXXXXXXXXX}) but do not correspond to rows in the
 * {@code banking.accounts} table — they exist only as ledger participants.
 * Treating them as ordinary customer accounts would be incorrect (they have
 * no owner, no Keycloak identity, and should never appear in customer-facing
 * account listings).
 */
public final class SystemAccounts {

    private SystemAccounts() {}

    /**
     * Represents the bank's cash/vault position. Debited when a customer
     * deposits money (cash flows in to the bank, but from the ledger's
     * perspective the cash account is "debited" as the source side of the
     * customer's credit), credited when a customer withdraws.
     */
    public static final AccountId CASH = AccountId.of("ACC-SYSCASH001");

    /**
     * Represents fee income earned by the bank. Credited whenever a fee is
     * charged to a customer account.
     */
    public static final AccountId FEE_INCOME = AccountId.of("ACC-SYSFEEINC1");

    /**
     * Represents interest expense — the cost to the bank of paying interest
     * to customers on interest-bearing accounts. Debited when interest is
     * credited to a customer.
     */
    public static final AccountId INTEREST_EXPENSE = AccountId.of("ACC-SYSINTEXP1");

    /**
     * Returns true if the given account ID refers to a system account rather
     * than a customer account. Used by the reporting module to exclude
     * system accounts from customer-facing account listings.
     */
    public static boolean isSystemAccount(AccountId accountId) {
        return CASH.equals(accountId)
            || FEE_INCOME.equals(accountId)
            || INTEREST_EXPENSE.equals(accountId);
    }

    public static boolean isSystemAccount(String accountIdValue) {
        return CASH.value().equals(accountIdValue)
            || FEE_INCOME.value().equals(accountIdValue)
            || INTEREST_EXPENSE.value().equals(accountIdValue);
    }
}

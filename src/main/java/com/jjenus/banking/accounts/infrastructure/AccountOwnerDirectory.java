package com.jjenus.banking.accounts.infrastructure;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Tracks the human-readable owner name for each account.
 *
 * <p>bank-core's {@code Account} record has a single {@code customerId} field,
 * which this application uses to store the Keycloak owner ID (needed for
 * "find my accounts" queries). The display name shown in statements and
 * emails is therefore tracked separately, here, as JPA-layer metadata that
 * never flows through the bank-core domain object.
 *
 * <p>Backed by the {@code owner_name} column on {@code AccountJpaEntity} — this
 * class is a thin, named query facade so callers don't need to know that
 * detail.
 */
@Component
public class AccountOwnerDirectory {

    private final AccountJpaRepository jpa;

    AccountOwnerDirectory(AccountJpaRepository jpa) {
        this.jpa = jpa;
    }

    /**
     * Records (or overwrites) the display name for an account.
     * Called once at account creation; the owner name does not change
     * over the lifetime of the account in the current design.
     */
    public void recordOwnerName(String accountId, String ownerName) {
        jpa.updateOwnerName(accountId, ownerName);
    }

    public Optional<String> getOwnerName(String accountId) {
        return jpa.findOwnerName(accountId);
    }
}

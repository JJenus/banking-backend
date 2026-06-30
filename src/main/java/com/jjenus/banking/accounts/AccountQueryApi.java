package com.jjenus.banking.accounts;

import java.util.Optional;

/**
 * Public API surface of the {@code accounts} module.
 *
 * <p>This interface is the ONLY way other modules may query account data.
 * No other module may inject {@code AccountJpaRepository},
 * {@code AccountRepositoryAdapter}, or {@code AccountApplicationService}
 * directly — that would cross a module boundary.
 *
 * <p>Used by {@code notifications} to resolve which Keycloak user owns an
 * account (so it can look up their email via {@code IdentityQueryApi}), and
 * by {@code reporting} to resolve display names for statements.
 */
public interface AccountQueryApi {

    /**
     * Returns the Keycloak owner ID (sub) for an account.
     *
     * @param accountId bank-core account ID, e.g. {@code ACC-XXXXXXXXXX}
     * @return the owner's Keycloak sub, or empty if the account does not exist
     */
    Optional<String> getOwnerId(String accountId);

    /**
     * Returns the display name recorded for an account's owner.
     *
     * @param accountId bank-core account ID
     * @return the owner's display name, or empty if not found
     */
    Optional<String> getOwnerName(String accountId);

    /**
     * Returns whether an account with the given ID exists.
     */
    boolean accountExists(String accountId);
}

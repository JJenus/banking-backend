package com.jjenus.banking.identity;

/**
 * Public API surface of the {@code identity} module.
 *
 * <p>This interface is the ONLY way other modules may query identity data.
 * No other module may inject {@code UserProfileJpaRepository} or
 * {@code IdentityApplicationService} directly — that would cross a module boundary.
 *
 * <p>Usage example from the {@code notifications} module:
 * <pre>
 *   private final IdentityQueryApi identityQueryApi;
 *
 *   String email = identityQueryApi.getEmailByUserId(userId)
 *       .orElseThrow(() -> new ResourceNotFoundException("User", userId));
 * </pre>
 *
 * <p>Usage from {@code notifications} to resolve an account owner's email:
 * <pre>
 *   String email = identityQueryApi.getEmailByAccountOwnerId(ownerId)
 *       .orElse("noreply@banking.local");
 * </pre>
 */
public interface IdentityQueryApi {

    /**
     * Returns the email address for a user identified by their Keycloak sub (UUID).
     *
     * @param userId Keycloak sub claim
     * @return the email, or empty if the user profile does not exist
     */
    java.util.Optional<String> getEmailByUserId(String userId);

    /**
     * Returns the full name for a user identified by their Keycloak sub (UUID).
     */
    java.util.Optional<String> getFullNameByUserId(String userId);

    /**
     * Returns whether a user profile exists for the given Keycloak sub.
     */
    boolean profileExists(String userId);
}

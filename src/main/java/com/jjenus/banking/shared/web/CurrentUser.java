package com.jjenus.banking.shared.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Utility for extracting the current authenticated user's details from
 * the Keycloak JWT within the request thread.
 *
 * <p>Usage in any controller or service:
 * <pre>
 *   String userId = CurrentUser.id();          // Keycloak sub (UUID)
 *   String email  = CurrentUser.email();
 *   boolean admin = CurrentUser.hasRole("ADMIN");
 * </pre>
 */
public final class CurrentUser {

    private CurrentUser() {}

    public static Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("No authenticated JWT found in security context");
        }
        return jwt;
    }

    /** Keycloak {@code sub} claim — stable UUID for this user. */
    public static String id() {
        return jwt().getSubject();
    }

    public static String email() {
        return jwt().getClaimAsString("email");
    }

    public static String preferredUsername() {
        return jwt().getClaimAsString("preferred_username");
    }

    public static String fullName() {
        return jwt().getClaimAsString("name");
    }

    public static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_" + role.toUpperCase()));
    }
}

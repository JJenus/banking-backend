/**
 * Identity module — user profiles and KYC verification.
 *
 * <p>Owns banking-specific profile data layered on top of Keycloak-authenticated
 * users: full name, phone number, and the KYC (Know Your Customer) verification
 * state machine ({@code PENDING → SUBMITTED → UNDER_REVIEW → APPROVED/REJECTED}).
 *
 * <p>Keycloak remains the source of truth for authentication (login, password,
 * MFA, sessions). This module never duplicates that — it only extends the
 * Keycloak {@code sub} claim with banking-relevant profile fields.
 *
 * <p>Public API: {@link com.jjenus.banking.identity.IdentityQueryApi}, implemented by
 * {@link com.jjenus.banking.identity.application.IdentityApplicationService}.
 * Other modules must depend only on this interface, never on
 * {@code UserProfileJpaRepository} or the application service directly.
 */
package com.jjenus.banking.identity;

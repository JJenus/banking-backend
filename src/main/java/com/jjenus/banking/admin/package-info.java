/**
 * Admin module — operational dashboard for ADMIN and COMPLIANCE roles.
 *
 * <p>This module has no domain logic of its own. It delegates to the
 * public APIs of other modules ({@code accounts}, {@code identity},
 * {@code audit}) to provide a single consolidated REST surface for
 * back-office operations.
 *
 * <p>All endpoints require at minimum {@code ROLE_ADMIN}. Read-only
 * endpoints (account lookup, KYC queue, audit log) also permit
 * {@code ROLE_COMPLIANCE}.
 */
package com.jjenus.banking.admin;

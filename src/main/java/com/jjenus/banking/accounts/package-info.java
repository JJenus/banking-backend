/**
 * Accounts module — account lifecycle and balance management.
 *
 * <p>Implements bank-core's {@code AccountRepository} port and orchestrates
 * commands through bank-core's {@code AccountService}. Contains no business
 * logic of its own — balance rules, status transitions, and validation all
 * live in the bank-core domain library.
 *
 * <p>Public API: {@link com.jjenus.banking.accounts.AccountQueryApi}, implemented by
 * {@link com.jjenus.banking.accounts.application.AccountApplicationService}.
 * Other modules must depend only on this interface, never on
 * {@code AccountJpaRepository} or {@code AccountRepositoryAdapter} directly.
 */
package com.jjenus.banking.accounts;

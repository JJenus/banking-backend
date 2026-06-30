/**
 * Transactions sub-module — account statement lines.
 *
 * <p>Transactions are a derived read model: each money movement produces one
 * statement line per account involved. They are created by bank-core's
 * {@code TransferService} and {@code AccountService} and persisted here by
 * {@code TransferApplicationService} and {@code AccountApplicationService}.
 *
 * <p>{@link com.jjenus.banking.transactions.infrastructure.TransactionRepository}
 * is accessible to the {@code accounts} and {@code transfers} modules because
 * those modules are responsible for creating transaction records during
 * money-movement operations. The {@code reporting} module reads transactions
 * via its own JPA query (permitted since {@code reporting} reads across
 * modules for statement generation, as documented in architecture.md).
 */
package com.jjenus.banking.transactions;

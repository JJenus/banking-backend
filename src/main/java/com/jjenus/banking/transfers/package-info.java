/**
 * Transfers module — money movement between accounts.
 *
 * <p>All transfer execution delegates to bank-core's {@code TransferService}.
 * This module owns idempotency enforcement (Redis, 24h TTL), persistence of
 * {@code Transfer} and {@code Transaction} records, and the REST API.
 *
 * <p>Every transfer produces two {@code Transaction} records (debit + credit)
 * and four {@code TransferEvent} domain events, all persisted atomically
 * within the same database transaction before the events are published.
 *
 * <p>There is no public cross-module API for the transfers module — other
 * modules consume transfer data via domain events ({@code TransferCompleted},
 * etc.) subscribed to through {@code @ApplicationModuleListener}.
 */
package com.jjenus.banking.transfers;

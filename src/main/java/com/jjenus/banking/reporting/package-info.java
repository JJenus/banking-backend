/**
 * Reporting module — account statements, trial balance, PDF export.
 *
 * <p>Entirely read-only. No commands, no domain events, no writes.
 * Assembles read models from data across three sources:
 * <ul>
 *   <li>{@code accounts} module via {@link com.jjenus.banking.accounts.AccountQueryApi}</li>
 *   <li>{@code transactions} table via {@link com.jjenus.banking.transactions.infrastructure.TransactionRepository}</li>
 *   <li>{@code ledger} module via {@link com.jjenus.banking.ledger.LedgerQueryApi}</li>
 * </ul>
 *
 * <p>PDF generation uses OpenPDF (LGPL 2.1 — commercial-safe, self-contained,
 * no external HTTP calls). The PDF is generated in-memory and returned
 * directly in the HTTP response — no files are written to disk.
 */
package com.jjenus.banking.reporting;

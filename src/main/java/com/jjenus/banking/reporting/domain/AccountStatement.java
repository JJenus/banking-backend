package com.jjenus.banking.reporting.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Account statement — a customer-facing summary of all movements on a single
 * account within a date range, with a running balance.
 *
 * <p>This is a read model, not a domain aggregate. It is assembled by
 * {@link com.jjenus.banking.reporting.application.ReportingApplicationService}
 * from the transactions table and the ledger, then returned directly
 * to the controller or rendered into a PDF.
 */
public record AccountStatement(
    String accountId,
    String ownerName,
    String currency,
    Instant periodFrom,
    Instant periodTo,
    BigDecimal openingBalance,
    BigDecimal closingBalance,
    List<StatementLine> lines,
    Instant generatedAt
) {

    /**
     * A single line on the statement. Each line corresponds to one
     * {@code Transaction} record.
     */
    public record StatementLine(
        String transactionId,
        Instant timestamp,
        String type,
        BigDecimal amount,        // positive = credit, negative = debit
        BigDecimal balanceAfter,
        String description,
        String reference
    ) {}
}

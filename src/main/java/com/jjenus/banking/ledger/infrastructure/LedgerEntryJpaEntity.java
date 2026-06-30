package com.jjenus.banking.ledger.infrastructure;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA persistence entity for a {@code LedgerEntry}.
 *
 * <p>This table is append-only by design — no service in this codebase
 * issues an UPDATE or DELETE against it. Corrections are made by posting a
 * new reversal entry (bank-core's {@code LedgerEntry.forReversal}), never by
 * modifying an existing row.
 *
 * <p>Flyway migration: V003__create_ledger_table.sql
 */
@Entity
@Table(
    name = "ledger_entries",
    schema = "banking",
    indexes = {
        @Index(name = "idx_ledger_debit_account",  columnList = "debit_account_id"),
        @Index(name = "idx_ledger_credit_account", columnList = "credit_account_id"),
        @Index(name = "idx_ledger_posted_at",      columnList = "posted_at"),
        @Index(name = "idx_ledger_source_id",      columnList = "source_id"),
        @Index(name = "idx_ledger_reference",      columnList = "reference")
    }
)
public class LedgerEntryJpaEntity {

    @Id
    @Column(name = "id", length = 20, nullable = false, updatable = false)
    private String id;                     // JNL-XXXXXXXXXXXX

    @Column(name = "debit_account_id", nullable = false, length = 36, updatable = false)
    private String debitAccountId;

    @Column(name = "credit_account_id", nullable = false, length = 36, updatable = false)
    private String creditAccountId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "description", nullable = false, length = 500, updatable = false)
    private String description;

    @Column(name = "reference", length = 100, updatable = false)
    private String reference;

    @Column(name = "source_id", length = 100, updatable = false)
    private String sourceId;

    @Column(name = "posted_at", nullable = false, updatable = false)
    private Instant postedAt;

    protected LedgerEntryJpaEntity() {}

    public LedgerEntryJpaEntity(String id, String debitAccountId, String creditAccountId,
                                BigDecimal amount, String currency, String description,
                                String reference, String sourceId, Instant postedAt) {
        this.id              = id;
        this.debitAccountId  = debitAccountId;
        this.creditAccountId = creditAccountId;
        this.amount          = amount;
        this.currency        = currency;
        this.description     = description;
        this.reference       = reference;
        this.sourceId        = sourceId;
        this.postedAt        = postedAt;
    }

    public String getId()               { return id; }
    public String getDebitAccountId()   { return debitAccountId; }
    public String getCreditAccountId()  { return creditAccountId; }
    public BigDecimal getAmount()       { return amount; }
    public String getCurrency()         { return currency; }
    public String getDescription()      { return description; }
    public String getReference()        { return reference; }
    public String getSourceId()         { return sourceId; }
    public Instant getPostedAt()        { return postedAt; }
}

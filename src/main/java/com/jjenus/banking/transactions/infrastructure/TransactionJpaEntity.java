package com.jjenus.banking.transactions.infrastructure;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity for a {@link com.jjenus.bank.core.transactions.Transaction}.
 *
 * <p>Transactions are statement lines — one row per debit or credit on a
 * single account. They are created by bank-core's {@code TransferService}
 * and persisted here by {@code TransferApplicationService}.
 *
 * <p>Flyway migration: V002__create_transfers_and_transactions_tables.sql
 */
@Entity
@Table(
    name = "transactions",
    schema = "banking",
    indexes = {
        @Index(name = "idx_transactions_account_id", columnList = "account_id"),
        @Index(name = "idx_transactions_type",       columnList = "type"),
        @Index(name = "idx_transactions_timestamp",  columnList = "timestamp"),
        @Index(name = "idx_transactions_reference",  columnList = "reference")
    }
)
public class TransactionJpaEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "account_id", nullable = false, length = 36, updatable = false)
    private String accountId;

    @Column(name = "type", nullable = false, length = 30, updatable = false)
    private String type;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal balanceAfter;

    @Column(name = "description", length = 500, updatable = false)
    private String description;

    @Column(name = "reference", length = 100, updatable = false)
    private String reference;

    @Column(name = "linked_tx_id", length = 36, updatable = false)
    private String linkedTxId;

    @Column(name = "metadata", columnDefinition = "TEXT", updatable = false)
    private String metadata;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;

    protected TransactionJpaEntity() {}

    public TransactionJpaEntity(String id, String accountId, String type,
                                 BigDecimal amount, String currency, BigDecimal balanceAfter,
                                 String description, String reference,
                                 String linkedTxId, String metadata, Instant timestamp) {
        this.id           = id;
        this.accountId    = accountId;
        this.type         = type;
        this.amount       = amount;
        this.currency     = currency;
        this.balanceAfter = balanceAfter;
        this.description  = description;
        this.reference    = reference;
        this.linkedTxId   = linkedTxId;
        this.metadata     = metadata;
        this.timestamp    = timestamp;
    }

    public String getId()              { return id; }
    public String getAccountId()       { return accountId; }
    public String getType()            { return type; }
    public BigDecimal getAmount()      { return amount; }
    public String getCurrency()        { return currency; }
    public BigDecimal getBalanceAfter(){ return balanceAfter; }
    public String getDescription()     { return description; }
    public String getReference()       { return reference; }
    public String getLinkedTxId()      { return linkedTxId; }
    public String getMetadata()        { return metadata; }
    public Instant getTimestamp()      { return timestamp; }
}

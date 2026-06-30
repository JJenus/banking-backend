package com.jjenus.banking.transfers.infrastructure;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity for a {@link com.jjenus.bank.core.transfers.Transfer}.
 * Flyway migration: V002__create_transfers_and_transactions_tables.sql
 */
@Entity
@Table(
    name = "transfers",
    schema = "banking",
    indexes = {
        @Index(name = "idx_transfers_from_account", columnList = "from_account_id"),
        @Index(name = "idx_transfers_to_account",   columnList = "to_account_id"),
        @Index(name = "idx_transfers_status",       columnList = "status"),
        @Index(name = "idx_transfers_reference",    columnList = "reference"),
        @Index(name = "idx_transfers_created_at",   columnList = "created_at")
    }
)
public class TransferJpaEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "from_account_id", nullable = false, length = 36, updatable = false)
    private String fromAccountId;

    @Column(name = "to_account_id", nullable = false, length = 36, updatable = false)
    private String toAccountId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "reference", nullable = false, length = 100, updatable = false)
    private String reference;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "debit_transaction_id", length = 36)
    private String debitTransactionId;

    @Column(name = "credit_transaction_id", length = 36)
    private String creditTransactionId;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected TransferJpaEntity() {}

    public TransferJpaEntity(String id, String fromAccountId, String toAccountId,
                              BigDecimal amount, String currency, String description,
                              String reference, String status, Instant createdAt) {
        this.id            = id;
        this.fromAccountId = fromAccountId;
        this.toAccountId   = toAccountId;
        this.amount        = amount;
        this.currency      = currency;
        this.description   = description;
        this.reference     = reference;
        this.status        = status;
        this.createdAt     = createdAt;
    }

    public String getId()                 { return id; }
    public String getFromAccountId()      { return fromAccountId; }
    public String getToAccountId()        { return toAccountId; }
    public BigDecimal getAmount()         { return amount; }
    public String getCurrency()           { return currency; }
    public String getDescription()        { return description; }
    public String getReference()          { return reference; }
    public String getStatus()             { return status; }
    public String getDebitTransactionId() { return debitTransactionId; }
    public String getCreditTransactionId(){ return creditTransactionId; }
    public String getFailureReason()      { return failureReason; }
    public Instant getCreatedAt()         { return createdAt; }
    public Instant getCompletedAt()       { return completedAt; }

    public void setStatus(String status)                          { this.status = status; }
    public void setDebitTransactionId(String debitTransactionId)  { this.debitTransactionId = debitTransactionId; }
    public void setCreditTransactionId(String creditTransactionId){ this.creditTransactionId = creditTransactionId; }
    public void setFailureReason(String failureReason)            { this.failureReason = failureReason; }
    public void setCompletedAt(Instant completedAt)               { this.completedAt = completedAt; }
}

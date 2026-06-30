package com.jjenus.banking.accounts.infrastructure;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA persistence entity for an {@code Account}.
 *
 * <p>This is the infrastructure representation. Domain logic uses
 * {@code com.jjenus.bank.core.accounts.Account} (immutable record from bank-core).
 * Mapping between the two is done in {@link AccountMapper}.
 *
 * <p>Flyway migration: {@code V001__create_accounts_table.sql}
 */
@Entity
@Table(
    name = "accounts",
    schema = "banking",
    indexes = {
        @Index(name = "idx_accounts_owner_id", columnList = "owner_id"),
        @Index(name = "idx_accounts_status",   columnList = "status")
    }
)
public class AccountJpaEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "owner_id", nullable = false, length = 36)
    private String ownerId;            // Keycloak sub (UUID)

    @Column(name = "owner_name", nullable = false, length = 200)
    private String ownerName;

    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;           // ISO 4217 currency code

    @Column(name = "status", nullable = false, length = 30)
    private String status;             // AccountStatus enum name

    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;

    protected AccountJpaEntity() {}

    public AccountJpaEntity(String id, String ownerId, String ownerName,
                             BigDecimal balance, String currency, String status,
                             Long version, Instant createdAt, Instant lastUpdatedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.balance = balance;
        this.currency = currency;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.lastUpdatedAt = lastUpdatedAt;
    }

    // Getters
    public String getId()            { return id; }
    public String getOwnerId()       { return ownerId; }
    public String getOwnerName()     { return ownerName; }
    public BigDecimal getBalance()   { return balance; }
    public String getCurrency()      { return currency; }
    public String getStatus()        { return status; }
    public Long getVersion()         { return version; }
    public Instant getCreatedAt()    { return createdAt; }
    public Instant getLastUpdatedAt(){ return lastUpdatedAt; }

    // Setters for JPA updates
    public void setBalance(BigDecimal balance)          { this.balance = balance; }
    public void setStatus(String status)                { this.status = status; }
    public void setVersion(Long version)                { this.version = version; }
    public void setLastUpdatedAt(Instant lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }
}

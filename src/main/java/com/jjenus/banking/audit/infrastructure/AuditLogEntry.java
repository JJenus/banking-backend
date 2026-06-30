package com.jjenus.banking.audit.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit log entry.
 *
 * <p>One row per domain event. Never updated, never deleted.
 * Flyway migration: {@code V004__create_audit_log_table.sql}
 */
@Entity
@Table(
    name = "audit_log",
    schema = "banking",
    indexes = {
        @Index(name = "idx_audit_aggregate_id",  columnList = "aggregate_id"),
        @Index(name = "idx_audit_event_type",    columnList = "event_type"),
        @Index(name = "idx_audit_actor",         columnList = "actor"),
        @Index(name = "idx_audit_occurred_at",   columnList = "occurred_at")
    }
)
public class AuditLogEntry {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private final String id;

    @Column(name = "event_type", nullable = false, length = 100)
    private final String eventType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private final String aggregateId;

    @Column(name = "actor", nullable = false, length = 100)
    private final String actor;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private final String payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private final Instant occurredAt;

    protected AuditLogEntry() {
        // JPA
        this.id = null; this.eventType = null; this.aggregateId = null;
        this.actor = null; this.payload = null; this.occurredAt = null;
    }

    public AuditLogEntry(String eventType, String aggregateId,
                         String actor, String payload, Instant occurredAt) {
        this.id          = UUID.randomUUID().toString();
        this.eventType   = eventType;
        this.aggregateId = aggregateId;
        this.actor       = actor;
        this.payload     = payload;
        this.occurredAt  = occurredAt;
    }

    public String getId()          { return id; }
    public String getEventType()   { return eventType; }
    public String getAggregateId() { return aggregateId; }
    public String getActor()       { return actor; }
    public String getPayload()     { return payload; }
    public Instant getOccurredAt() { return occurredAt; }
}

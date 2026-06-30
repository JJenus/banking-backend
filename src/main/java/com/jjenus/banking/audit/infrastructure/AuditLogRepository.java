package com.jjenus.banking.audit.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, String> {
    List<AuditLogEntry> findByAggregateIdOrderByOccurredAtAsc(String aggregateId);
    List<AuditLogEntry> findByActorOrderByOccurredAtDesc(String actor);
    List<AuditLogEntry> findByEventTypeAndOccurredAtBetween(String eventType, Instant from, Instant to);
}

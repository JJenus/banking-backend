package com.jjenus.banking.audit.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjenus.bank.core.shared.DomainEvent;
import com.jjenus.banking.audit.infrastructure.AuditLogEntry;
import com.jjenus.banking.audit.infrastructure.AuditLogRepository;
import com.jjenus.banking.shared.web.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Audit event listener.
 *
 * <p>Subscribes to every {@link DomainEvent} emitted anywhere in the application
 * and writes an immutable, append-only {@link AuditLogEntry} row.
 *
 * <p>The audit log never mutates or deletes records. It is the compliance trail.
 *
 * <p>Uses {@code @ApplicationModuleListener} to ensure events are captured
 * <em>after</em> the originating transaction commits, providing a consistent
 * view of state changes.
 */
@Component
public class AuditListener {

    private static final Logger log = LoggerFactory.getLogger(AuditListener.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditListener(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper       = objectMapper;
    }

    @ApplicationModuleListener
    public void onDomainEvent(DomainEvent event) {
        try {
            String eventType   = event.getClass().getSimpleName();
            String aggregateId = extractAggregateId(event);
            String actor       = resolveActor();
            String payload     = objectMapper.writeValueAsString(event);

            AuditLogEntry entry = new AuditLogEntry(
                eventType,
                aggregateId,
                actor,
                payload,
                Instant.now()
            );

            auditLogRepository.save(entry);
            log.debug("Audit log written: {} for aggregate {}", eventType, aggregateId);

        } catch (Exception e) {
            log.error("Failed to write audit log for event {}: {}", event.getClass().getSimpleName(), e.getMessage(), e);
            // Audit failure is logged but must not cause the business operation to fail
        }
    }

    private String extractAggregateId(DomainEvent event) {
        // Reflectively extract the aggregate ID if available, else fall back to event ID
        try {
            var idMethod = event.getClass().getMethod("accountId");
            return idMethod.invoke(event).toString();
        } catch (NoSuchMethodException ignored) {}
        try {
            var idMethod = event.getClass().getMethod("transferId");
            return idMethod.invoke(event).toString();
        } catch (Exception ignored) {}
        return event.eventId().value();
    }

    private String resolveActor() {
        try {
            return CurrentUser.id();
        } catch (Exception e) {
            return "SYSTEM";
        }
    }
}

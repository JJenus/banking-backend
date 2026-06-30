package com.jjenus.banking.identity.domain;

import com.jjenus.bank.core.shared.DomainEvent;
import com.jjenus.bank.core.shared.Id;

import java.time.Instant;

/**
 * Domain events emitted by the {@code identity} module's KYC state machine.
 *
 * <p>Consistent with bank-core's event pattern: sealed interface, record
 * subtypes, static factory methods. These events are application-level
 * (not part of bank-core) because KYC is specific to this banking
 * application, not a universal banking domain concept.
 */
public sealed interface IdentityEvent extends DomainEvent {

    String userId();

    static KycSubmitted kycSubmitted(String userId) {
        return new KycSubmitted(Id.random(), Instant.now(), userId);
    }

    static KycApproved kycApproved(String userId) {
        return new KycApproved(Id.random(), Instant.now(), userId);
    }

    static KycRejected kycRejected(String userId, String reason) {
        return new KycRejected(Id.random(), Instant.now(), userId, reason);
    }

    record KycSubmitted(
        Id<DomainEvent> eventId,
        Instant occurredOn,
        String userId
    ) implements IdentityEvent {}

    record KycApproved(
        Id<DomainEvent> eventId,
        Instant occurredOn,
        String userId
    ) implements IdentityEvent {}

    record KycRejected(
        Id<DomainEvent> eventId,
        Instant occurredOn,
        String userId,
        String reason
    ) implements IdentityEvent {}
}

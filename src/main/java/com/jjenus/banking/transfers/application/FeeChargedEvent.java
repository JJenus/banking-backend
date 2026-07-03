package com.jjenus.banking.transfers.application;

import com.jjenus.bank.core.accounts.AccountId;
import com.jjenus.bank.core.shared.DomainEvent;
import com.jjenus.bank.core.shared.Id;
import com.jjenus.bank.core.shared.Money;
import com.jjenus.bank.core.transfers.TransferId;

import java.time.Instant;

/**
 * Domain event emitted by {@link TransferApplicationService} when a non-zero
 * fee is charged to the sender account following a transfer.
 *
 * <p>Consumed by:
 * <ul>
 *   <li>{@code LedgerListener} — posts a FEE journal entry
 *       (debit sender, credit FEE_INCOME system account)</li>
 *   <li>{@code AuditListener} — captures the fee charge in the audit log</li>
 * </ul>
 */
public record FeeChargedEvent(
    Id<DomainEvent> eventId,
    Instant occurredOn,
    AccountId chargedAccountId,
    Money feeAmount,
    TransferId relatedTransferId,
    String feePolicyDescription
) implements DomainEvent {

    /**
     * Convenience constructor — generates eventId and occurredOn automatically.
     */
    public FeeChargedEvent(AccountId chargedAccountId, Money feeAmount,
                           TransferId relatedTransferId, String feePolicyDescription) {
        this(Id.random(), Instant.now(),
             chargedAccountId, feeAmount, relatedTransferId, feePolicyDescription);
    }
}

package com.jjenus.banking.sse.application;

import com.jjenus.bank.core.accounts.AccountEvent;
import com.jjenus.bank.core.transfers.TransferEvent;
import com.jjenus.banking.accounts.AccountQueryApi;
import com.jjenus.banking.identity.IdentityQueryApi;
import com.jjenus.banking.identity.domain.IdentityEvent;
import com.jjenus.banking.transfers.application.FeeChargedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Routes domain events from all modules to connected SSE clients.
 *
 * <p>Each handler resolves the Keycloak owner ID for the affected account/user
 * and pushes a typed payload via {@link SseEmitterRegistry}.
 *
 * <p>Uses {@code @ApplicationModuleListener} — fires after the originating
 * transaction commits. A disconnected client never affects the business operation.
 *
 * <p>Payload types sent (all serialised as JSON in the SSE {@code data:} field):
 * <ul>
 *   <li>{@code BALANCE_UPDATED}       — after any money movement</li>
 *   <li>{@code TRANSACTION_CREATED}   — deposit, withdrawal, transfer credit</li>
 *   <li>{@code TRANSFER_COMPLETED}    — transfer fully settled</li>
 *   <li>{@code TRANSFER_FAILED}       — transfer rejected</li>
 *   <li>{@code TRANSFER_REVERSED}     — transfer reversed</li>
 *   <li>{@code ACCOUNT_STATUS_CHANGED}— frozen, activated, suspended, closed</li>
 *   <li>{@code KYC_STATUS_CHANGED}    — KYC approved or rejected</li>
 *   <li>{@code FEE_CHARGED}           — a fee was deducted</li>
 * </ul>
 */
@Component
public class SseEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SseEventDispatcher.class);

    private final SseEmitterRegistry registry;
    private final AccountQueryApi accountQueryApi;
    private final IdentityQueryApi identityQueryApi;

    public SseEventDispatcher(SseEmitterRegistry registry,
                              AccountQueryApi accountQueryApi,
                              IdentityQueryApi identityQueryApi) {
        this.registry        = registry;
        this.accountQueryApi = accountQueryApi;
        this.identityQueryApi = identityQueryApi;
    }

    // ── Account events ────────────────────────────────────────────────────

    @ApplicationModuleListener
    public void onMoneyDeposited(AccountEvent.MoneyDeposited event) {
        resolveOwner(event.accountId().value()).ifPresent(ownerId -> {
            registry.sendToUser(ownerId, "TRANSACTION_CREATED",
                new TransactionPayload(
                    event.accountId().value(), "DEPOSIT",
                    event.amount().amount().toPlainString(),
                    event.amount().currency().getCurrencyCode(),
                    null, event.reference()
                ));
            registry.sendToUser(ownerId, "BALANCE_UPDATED",
                new BalancePayload(event.accountId().value(),
                    event.amount().currency().getCurrencyCode()));
        });
    }

    @ApplicationModuleListener
    public void onMoneyWithdrawn(AccountEvent.MoneyWithdrawn event) {
        resolveOwner(event.accountId().value()).ifPresent(ownerId -> {
            registry.sendToUser(ownerId, "TRANSACTION_CREATED",
                new TransactionPayload(
                    event.accountId().value(), "WITHDRAWAL",
                    event.amount().amount().negate().toPlainString(),
                    event.amount().currency().getCurrencyCode(),
                    null, event.reference()
                ));
            registry.sendToUser(ownerId, "BALANCE_UPDATED",
                new BalancePayload(event.accountId().value(),
                    event.amount().currency().getCurrencyCode()));
        });
    }

    @ApplicationModuleListener
    public void onAccountFrozen(AccountEvent.AccountFrozen event) {
        resolveOwner(event.accountId().value()).ifPresent(ownerId ->
            registry.sendToUser(ownerId, "ACCOUNT_STATUS_CHANGED",
                new AccountStatusPayload(event.accountId().value(), "FROZEN", event.reason())));
    }

    @ApplicationModuleListener
    public void onAccountActivated(AccountEvent.AccountActivated event) {
        resolveOwner(event.accountId().value()).ifPresent(ownerId ->
            registry.sendToUser(ownerId, "ACCOUNT_STATUS_CHANGED",
                new AccountStatusPayload(event.accountId().value(), "ACTIVE", null)));
    }

    @ApplicationModuleListener
    public void onAccountSuspended(AccountEvent.AccountSuspended event) {
        resolveOwner(event.accountId().value()).ifPresent(ownerId ->
            registry.sendToUser(ownerId, "ACCOUNT_STATUS_CHANGED",
                new AccountStatusPayload(event.accountId().value(), "SUSPENDED", event.reason())));
    }

    @ApplicationModuleListener
    public void onAccountClosed(AccountEvent.AccountClosed event) {
        resolveOwner(event.accountId().value()).ifPresent(ownerId ->
            registry.sendToUser(ownerId, "ACCOUNT_STATUS_CHANGED",
                new AccountStatusPayload(event.accountId().value(), "CLOSED", event.reason())));
    }

    // ── Transfer events ───────────────────────────────────────────────────

    @ApplicationModuleListener
    public void onTransferCompleted(TransferEvent.TransferCompleted event) {
        // Notify sender
        resolveOwner(event.fromAccountId().value()).ifPresent(ownerId ->
            registry.sendToUser(ownerId, "TRANSFER_COMPLETED",
                new TransferPayload(
                    event.transferId().value(),
                    event.fromAccountId().value(),
                    event.toAccountId().value(),
                    event.amount().amount().toPlainString(),
                    event.amount().currency().getCurrencyCode(),
                    "COMPLETED", "SENT"
                )));

        // Notify receiver — credit leg
        resolveOwner(event.toAccountId().value()).ifPresent(ownerId -> {
            registry.sendToUser(ownerId, "TRANSACTION_CREATED",
                new TransactionPayload(
                    event.toAccountId().value(), "TRANSFER_IN",
                    event.amount().amount().toPlainString(),
                    event.amount().currency().getCurrencyCode(),
                    event.transferId().value(), null
                ));
            registry.sendToUser(ownerId, "TRANSFER_COMPLETED",
                new TransferPayload(
                    event.transferId().value(),
                    event.fromAccountId().value(),
                    event.toAccountId().value(),
                    event.amount().amount().toPlainString(),
                    event.amount().currency().getCurrencyCode(),
                    "COMPLETED", "RECEIVED"
                ));
            registry.sendToUser(ownerId, "BALANCE_UPDATED",
                new BalancePayload(event.toAccountId().value(),
                    event.amount().currency().getCurrencyCode()));
        });
    }

    @ApplicationModuleListener
    public void onTransferFailed(TransferEvent.TransferFailed event) {
        // We don't know the fromAccountId from TransferFailed — resolved via registry
        // if the client is subscribed. Broadcast to all users watching that transfer.
        log.debug("SSE: transfer failed {}", event.transferId().value());
        // Without account context we can't resolve owner here — the client will
        // notice via the next balance poll or re-fetch. TransferFailed is already
        // surfaced via NotificationListener (email).
    }

    @ApplicationModuleListener
    public void onTransferReversed(TransferEvent.TransferReversed event) {
        // Notify original sender (receives money back)
        resolveOwner(event.originalFromAccountId().value()).ifPresent(ownerId ->
            registry.sendToUser(ownerId, "TRANSFER_REVERSED",
                new TransferPayload(
                    event.transferId().value(),
                    event.originalFromAccountId().value(),
                    event.originalToAccountId().value(),
                    event.amount().amount().toPlainString(),
                    event.amount().currency().getCurrencyCode(),
                    "REVERSED", "REFUNDED"
                )));

        // Notify original receiver (money debited back)
        resolveOwner(event.originalToAccountId().value()).ifPresent(ownerId ->
            registry.sendToUser(ownerId, "TRANSFER_REVERSED",
                new TransferPayload(
                    event.transferId().value(),
                    event.originalFromAccountId().value(),
                    event.originalToAccountId().value(),
                    event.amount().amount().toPlainString(),
                    event.amount().currency().getCurrencyCode(),
                    "REVERSED", "DEBITED"
                )));
    }

    // ── Fee events ────────────────────────────────────────────────────────

    @ApplicationModuleListener
    public void onFeeCharged(FeeChargedEvent event) {
        resolveOwner(event.chargedAccountId().value()).ifPresent(ownerId ->
            registry.sendToUser(ownerId, "FEE_CHARGED",
                new FeePayload(
                    event.chargedAccountId().value(),
                    event.feeAmount().amount().toPlainString(),
                    event.feeAmount().currency().getCurrencyCode(),
                    event.feePolicyDescription()
                )));
    }

    // ── KYC / Identity events ─────────────────────────────────────────────

    @ApplicationModuleListener
    public void onKycApproved(IdentityEvent.KycApproved event) {
        registry.sendToUser(event.userId(), "KYC_STATUS_CHANGED",
            new KycPayload(event.userId(), "APPROVED", null));
    }

    @ApplicationModuleListener
    public void onKycRejected(IdentityEvent.KycRejected event) {
        registry.sendToUser(event.userId(), "KYC_STATUS_CHANGED",
            new KycPayload(event.userId(), "REJECTED", event.reason()));
    }

    @ApplicationModuleListener
    public void onKycSubmitted(IdentityEvent.KycSubmitted event) {
        registry.sendToUser(event.userId(), "KYC_STATUS_CHANGED",
            new KycPayload(event.userId(), "SUBMITTED", null));
    }

    // ── Resolution helper ─────────────────────────────────────────────────

    /**
     * Resolves the Keycloak owner ID for an account.
     * Returns empty if the account doesn't exist or has no registered profile.
     */
    private Optional<String> resolveOwner(String accountId) {
        return accountQueryApi.getOwnerId(accountId);
    }

    // ── Payload records ───────────────────────────────────────────────────

    public record TransactionPayload(
        String accountId, String type,
        String amount, String currency,
        String relatedTransferId, String reference
    ) {}

    public record BalancePayload(String accountId, String currency) {}

    public record TransferPayload(
        String transferId, String fromAccountId, String toAccountId,
        String amount, String currency, String status, String direction
    ) {}

    public record AccountStatusPayload(
        String accountId, String newStatus, String reason
    ) {}

    public record KycPayload(String userId, String newStatus, String reason) {}

    public record FeePayload(
        String accountId, String amount,
        String currency, String policyDescription
    ) {}
}

package com.jjenus.banking.notifications.listeners;

import com.jjenus.bank.core.accounts.AccountEvent;
import com.jjenus.bank.core.transfers.TransferEvent;
import com.jjenus.banking.identity.domain.IdentityEvent;
import com.jjenus.banking.notifications.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Notification event listener.
 *
 * <p>Subscribes to domain events published by the {@code accounts} and
 * {@code transfers} modules and dispatches email notifications via
 * {@link EmailService} → Postal SMTP → customer inbox.
 *
 * <p>Uses {@code @ApplicationModuleListener} (Spring Modulith) instead of
 * plain {@code @EventListener}. This ensures:
 * <ul>
 *   <li>Events are processed <strong>after</strong> the originating transaction commits</li>
 *   <li>If email dispatch fails, the original transaction is not rolled back</li>
 *   <li>Failed events are retried by the Spring Modulith event publication log</li>
 * </ul>
 *
 * <p>This class has no REST endpoints, no outbound HTTP, no external APIs.
 * All email is routed through the self-hosted Postal SMTP server.
 */
@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final EmailService emailService;

    public NotificationListener(EmailService emailService) {
        this.emailService = emailService;
    }

    // ── Account events ────────────────────────────────────────────────────

    @ApplicationModuleListener
    public void onAccountCreated(AccountEvent.AccountCreated event) {
        log.info("Account created notification for account {}", event.accountId().value());
        emailService.sendAccountOpenedEmail(event.accountId().value(), event.ownerName());
    }

    @ApplicationModuleListener
    public void onMoneyDeposited(AccountEvent.MoneyDeposited event) {
        log.info("Deposit notification for account {}, amount {}",
            event.accountId().value(), event.amount().format());
        emailService.sendDepositConfirmationEmail(
            event.accountId().value(),
            event.amount().format(),
            event.reference()
        );
    }

    @ApplicationModuleListener
    public void onMoneyWithdrawn(AccountEvent.MoneyWithdrawn event) {
        log.info("Withdrawal notification for account {}", event.accountId().value());
        emailService.sendWithdrawalConfirmationEmail(
            event.accountId().value(),
            event.amount().format(),
            event.reference()
        );
    }

    @ApplicationModuleListener
    public void onAccountFrozen(AccountEvent.AccountFrozen event) {
        log.warn("Account frozen notification for account {}", event.accountId().value());
        emailService.sendAccountRestrictedEmail(event.accountId().value(), event.reason());
    }

    @ApplicationModuleListener
    public void onAccountActivated(AccountEvent.AccountActivated event) {
        log.info("Account activated notification for account {}", event.accountId().value());
        emailService.sendAccountActivatedEmail(event.accountId().value());
    }

    @ApplicationModuleListener
    public void onAccountClosed(AccountEvent.AccountClosed event) {
        log.info("Account closed notification for account {}", event.accountId().value());
        emailService.sendAccountClosedEmail(event.accountId().value(), event.reason());
    }

    // ── Transfer events ───────────────────────────────────────────────────

    @ApplicationModuleListener
    public void onTransferCompleted(TransferEvent.TransferCompleted event) {
        log.info("Transfer completed notification for transfer {}", event.transferId().value());
        emailService.sendTransferCompletedEmail(
            event.fromAccountId().value(),
            event.toAccountId().value(),
            event.amount().format(),
            event.transferId().value()
        );
    }

    @ApplicationModuleListener
    public void onTransferReversed(TransferEvent.TransferReversed event) {
        log.info("Transfer reversed notification for transfer {}", event.transferId().value());
        emailService.sendTransferReversedEmail(
            event.originalFromAccountId().value(),
            event.originalToAccountId().value(),
            event.amount().format(),
            event.reason()
        );
    }

    @ApplicationModuleListener
    public void onTransferFailed(TransferEvent.TransferFailed event) {
        log.warn("Transfer failed notification for transfer {}", event.transferId().value());
        emailService.sendTransferFailedEmail(
            event.transferId().value(),
            event.reason()
        );
    }

    // ── Identity / KYC events ─────────────────────────────────────────────

    @ApplicationModuleListener
    public void onKycSubmitted(IdentityEvent.KycSubmitted event) {
        log.info("KYC submitted notification for user {}", event.userId());
        emailService.sendKycSubmittedEmail(event.userId());
    }

    @ApplicationModuleListener
    public void onKycApproved(IdentityEvent.KycApproved event) {
        log.info("KYC approved notification for user {}", event.userId());
        emailService.sendKycApprovedEmail(event.userId());
    }

    @ApplicationModuleListener
    public void onKycRejected(IdentityEvent.KycRejected event) {
        log.warn("KYC rejected notification for user {}: {}", event.userId(), event.reason());
        emailService.sendKycRejectedEmail(event.userId(), event.reason());
    }
}

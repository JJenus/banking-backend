package com.jjenus.banking.ledger.application;

import com.jjenus.bank.core.accounts.AccountEvent;
import com.jjenus.bank.core.ledger.LedgerEntry;
import com.jjenus.bank.core.ledger.LedgerEntryId;
import com.jjenus.bank.core.ports.LedgerRepository;
import com.jjenus.bank.core.transfers.TransferEvent;
import com.jjenus.banking.ledger.domain.SystemAccounts;
import com.jjenus.banking.transfers.application.FeeChargedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ledger event listener.
 *
 * <p>This is the only place in the application where {@link LedgerEntry}
 * objects are created and posted. It listens for domain events from the
 * {@code accounts} and {@code transfers} modules and translates each one
 * into a balanced double-entry journal entry.
 *
 * <p>Mapping:
 * <ul>
 *   <li>{@code MoneyDeposited}  → debit {@link SystemAccounts#CASH}, credit the account</li>
 *   <li>{@code MoneyWithdrawn}  → debit the account, credit {@link SystemAccounts#CASH}</li>
 *   <li>{@code TransferCompleted} → debit the sender, credit the receiver</li>
 *   <li>{@code TransferReversed}  → debit the original receiver, credit the original sender</li>
 * </ul>
 *
 * <p>Uses {@code @ApplicationModuleListener} so entries are posted only
 * after the originating transaction (in {@code accounts} or {@code transfers})
 * has committed. If ledger posting fails, it does not roll back the already
 * -committed account balance change — instead it is logged loudly, since a
 * missing ledger entry against a committed balance change is a reconciliation
 * incident that needs operator attention, not a silent retry.
 */
@Component
public class LedgerListener {

    private static final Logger log = LoggerFactory.getLogger(LedgerListener.class);

    private final LedgerRepository ledgerRepository;

    public LedgerListener(LedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @ApplicationModuleListener
    @Transactional
    public void onMoneyDeposited(AccountEvent.MoneyDeposited event) {
        try {
            LedgerEntry entry = LedgerEntry.forDeposit(
                LedgerEntryId.generate(),
                SystemAccounts.CASH,
                event.accountId(),
                event.amount(),
                event.reference(),
                event.eventId().value()
            );
            ledgerRepository.post(entry);
            log.debug("Posted deposit ledger entry {} for account {}",
                entry.id().value(), event.accountId().value());
        } catch (IllegalArgumentException duplicateOrInvalid) {
            log.warn("Skipped duplicate/invalid deposit ledger entry for account {}: {}",
                event.accountId().value(), duplicateOrInvalid.getMessage());
        } catch (Exception e) {
            log.error("RECONCILIATION ALERT: failed to post deposit ledger entry for account {} "
                + "amount {} — balance was updated but ledger was not. Manual reconciliation required.",
                event.accountId().value(), event.amount().format(), e);
        }
    }

    @ApplicationModuleListener
    @Transactional
    public void onMoneyWithdrawn(AccountEvent.MoneyWithdrawn event) {
        try {
            LedgerEntry entry = LedgerEntry.forWithdrawal(
                LedgerEntryId.generate(),
                event.accountId(),
                SystemAccounts.CASH,
                event.amount(),
                event.reference(),
                event.eventId().value()
            );
            ledgerRepository.post(entry);
            log.debug("Posted withdrawal ledger entry {} for account {}",
                entry.id().value(), event.accountId().value());
        } catch (IllegalArgumentException duplicateOrInvalid) {
            log.warn("Skipped duplicate/invalid withdrawal ledger entry for account {}: {}",
                event.accountId().value(), duplicateOrInvalid.getMessage());
        } catch (Exception e) {
            log.error("RECONCILIATION ALERT: failed to post withdrawal ledger entry for account {} "
                + "amount {} — balance was updated but ledger was not. Manual reconciliation required.",
                event.accountId().value(), event.amount().format(), e);
        }
    }

    @ApplicationModuleListener
    @Transactional
    public void onTransferCompleted(TransferEvent.TransferCompleted event) {
        try {
            LedgerEntry entry = LedgerEntry.forTransfer(
                LedgerEntryId.generate(),
                event.fromAccountId(),
                event.toAccountId(),
                event.amount(),
                event.transferId().value(),
                event.transferId().value()
            );
            ledgerRepository.post(entry);
            log.debug("Posted transfer ledger entry {} for transfer {}",
                entry.id().value(), event.transferId().value());
        } catch (IllegalArgumentException duplicateOrInvalid) {
            log.warn("Skipped duplicate/invalid transfer ledger entry for transfer {}: {}",
                event.transferId().value(), duplicateOrInvalid.getMessage());
        } catch (Exception e) {
            log.error("RECONCILIATION ALERT: failed to post transfer ledger entry for transfer {} "
                + "amount {} — account balances were updated but ledger was not. "
                + "Manual reconciliation required.",
                event.transferId().value(), event.amount().format(), e);
        }
    }

    @ApplicationModuleListener
    @Transactional
    public void onTransferReversed(TransferEvent.TransferReversed event) {
        try {
            // Mirror image of the original transfer entry: debit the original
            // receiver, credit the original sender.
            LedgerEntry entry = LedgerEntry.forTransfer(
                LedgerEntryId.generate(),
                event.originalToAccountId(),
                event.originalFromAccountId(),
                event.amount(),
                "REVERSAL-" + event.transferId().value(),
                event.transferId().value()
            );
            ledgerRepository.post(entry);
            log.debug("Posted reversal ledger entry {} for transfer {}",
                entry.id().value(), event.transferId().value());
        } catch (IllegalArgumentException duplicateOrInvalid) {
            log.warn("Skipped duplicate/invalid reversal ledger entry for transfer {}: {}",
                event.transferId().value(), duplicateOrInvalid.getMessage());
        } catch (Exception e) {
            log.error("RECONCILIATION ALERT: failed to post reversal ledger entry for transfer {} "
                + "amount {} — account balances were reversed but ledger was not. "
                + "Manual reconciliation required.",
                event.transferId().value(), event.amount().format(), e);
        }
    }

    @ApplicationModuleListener
    @Transactional
    public void onFeeCharged(FeeChargedEvent event) {
        try {
            LedgerEntry entry = LedgerEntry.forFee(
                LedgerEntryId.generate(),
                event.chargedAccountId(),
                SystemAccounts.FEE_INCOME,
                event.feeAmount(),
                "Transfer fee: " + event.feePolicyDescription(),
                event.relatedTransferId().value()
            );
            ledgerRepository.post(entry);
            log.debug("Posted fee ledger entry {} for account {} amount {}",
                entry.id().value(),
                event.chargedAccountId().value(),
                event.feeAmount().format());
        } catch (IllegalArgumentException duplicateOrInvalid) {
            log.warn("Skipped duplicate/invalid fee ledger entry for transfer {}: {}",
                event.relatedTransferId().value(), duplicateOrInvalid.getMessage());
        } catch (Exception e) {
            log.error("RECONCILIATION ALERT: failed to post fee ledger entry for transfer {} "
                + "amount {} — fee was charged but ledger was not updated. "
                + "Manual reconciliation required.",
                event.relatedTransferId().value(), event.feeAmount().format(), e);
        }
    }
}

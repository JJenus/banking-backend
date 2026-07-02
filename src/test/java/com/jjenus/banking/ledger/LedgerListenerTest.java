package com.jjenus.banking.ledger;

import com.jjenus.bank.core.accounts.AccountEvent;
import com.jjenus.bank.core.accounts.AccountId;
import com.jjenus.bank.core.ledger.LedgerEntry;
import com.jjenus.bank.core.ports.LedgerRepository;
import com.jjenus.bank.core.shared.Id;
import com.jjenus.bank.core.shared.Money;
import com.jjenus.bank.core.transactions.TransactionId;
import com.jjenus.bank.core.transfers.TransferEvent;
import com.jjenus.bank.core.transfers.TransferId;
import com.jjenus.banking.ledger.application.LedgerListener;
import com.jjenus.banking.ledger.domain.SystemAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LedgerListener")
class LedgerListenerTest {

    private static final Currency NGN = Currency.getInstance("NGN");

    @Mock LedgerRepository ledgerRepository;

    LedgerListener listener;

    @BeforeEach
    void setUp() {
        listener = new LedgerListener(ledgerRepository);
        when(ledgerRepository.post(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── MoneyDeposited ────────────────────────────────────────────────────

    @Test
    @DisplayName("onMoneyDeposited posts debit=CASH, credit=account entry")
    void onMoneyDeposited_postsCorrectEntry() {
        AccountId accountId = AccountId.generate();
        Money amount = Money.of("500.00", NGN);
        AccountEvent.MoneyDeposited event = AccountEvent.moneyDeposited(accountId, amount, "DEP-001");

        listener.onMoneyDeposited(event);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).post(captor.capture());
        LedgerEntry entry = captor.getValue();

        assertThat(entry.debitAccountId()).isEqualTo(SystemAccounts.CASH);
        assertThat(entry.creditAccountId()).isEqualTo(accountId);
        assertThat(entry.amount().amount()).isEqualByComparingTo("500.0000");
        assertThat(entry.reference()).isEqualTo("DEP-001");
    }

    // ── MoneyWithdrawn ────────────────────────────────────────────────────

    @Test
    @DisplayName("onMoneyWithdrawn posts debit=account, credit=CASH entry")
    void onMoneyWithdrawn_postsCorrectEntry() {
        AccountId accountId = AccountId.generate();
        Money amount = Money.of("200.00", NGN);
        AccountEvent.MoneyWithdrawn event = AccountEvent.moneyWithdrawn(accountId, amount, "WITH-001");

        listener.onMoneyWithdrawn(event);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).post(captor.capture());
        LedgerEntry entry = captor.getValue();

        assertThat(entry.debitAccountId()).isEqualTo(accountId);
        assertThat(entry.creditAccountId()).isEqualTo(SystemAccounts.CASH);
        assertThat(entry.amount().amount()).isEqualByComparingTo("200.0000");
    }

    // ── TransferCompleted ─────────────────────────────────────────────────

    @Test
    @DisplayName("onTransferCompleted posts debit=sender, credit=receiver entry")
    void onTransferCompleted_postsCorrectEntry() {
        AccountId fromId = AccountId.generate();
        AccountId toId   = AccountId.generate();
        TransferId tfId  = TransferId.generate();
        Money amount     = Money.of("1000.00", NGN);

        TransferEvent.TransferCompleted event = TransferEvent.transferCompleted(
            tfId, fromId, toId, amount);

        listener.onTransferCompleted(event);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).post(captor.capture());
        LedgerEntry entry = captor.getValue();

        assertThat(entry.debitAccountId()).isEqualTo(fromId);
        assertThat(entry.creditAccountId()).isEqualTo(toId);
        assertThat(entry.amount().amount()).isEqualByComparingTo("1000.0000");
    }

    // ── TransferReversed ──────────────────────────────────────────────────

    @Test
    @DisplayName("onTransferReversed posts mirror entry: debit=original receiver, credit=original sender")
    void onTransferReversed_postsMirrorEntry() {
        AccountId fromId = AccountId.generate();
        AccountId toId   = AccountId.generate();
        TransferId tfId  = TransferId.generate();
        Money amount     = Money.of("750.00", NGN);
        TransactionId debitTxId  = TransactionId.generate();
        TransactionId creditTxId = TransactionId.generate();

        TransferEvent.TransferReversed event = TransferEvent.transferReversed(
            tfId, fromId, toId, amount, "Wrong recipient", debitTxId, creditTxId);

        listener.onTransferReversed(event);

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository).post(captor.capture());
        LedgerEntry entry = captor.getValue();

        // Reversal: debit the original recipient (toId), credit the original sender (fromId)
        assertThat(entry.debitAccountId()).isEqualTo(toId);
        assertThat(entry.creditAccountId()).isEqualTo(fromId);
        assertThat(entry.amount().amount()).isEqualByComparingTo("750.0000");
    }

    // ── Duplicate handling ────────────────────────────────────────────────

    @Test
    @DisplayName("duplicate ledger entry (IllegalArgumentException) is swallowed — no exception propagated")
    void onMoneyDeposited_duplicateEntry_swallowed() {
        when(ledgerRepository.post(any()))
            .thenThrow(new IllegalArgumentException("Duplicate ledger entry"));

        AccountEvent.MoneyDeposited event = AccountEvent.moneyDeposited(
            AccountId.generate(), Money.of("100.00", NGN), "DUP-001");

        // Must not throw — duplicate entries are expected for at-least-once delivery
        assertThatCode(() -> listener.onMoneyDeposited(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("unexpected exception is logged but not propagated")
    void onMoneyDeposited_unexpectedException_notPropagated() {
        when(ledgerRepository.post(any())).thenThrow(new RuntimeException("DB down"));

        AccountEvent.MoneyDeposited event = AccountEvent.moneyDeposited(
            AccountId.generate(), Money.of("100.00", NGN), "ERR-001");

        assertThatCode(() -> listener.onMoneyDeposited(event)).doesNotThrowAnyException();
    }
}

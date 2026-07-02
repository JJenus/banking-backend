package com.jjenus.banking.transfers;

import com.jjenus.bank.core.accounts.Account;
import com.jjenus.bank.core.accounts.AccountId;
import com.jjenus.bank.core.accounts.AccountStatus;
import com.jjenus.bank.core.ports.AccountRepository;
import com.jjenus.bank.core.ports.TransferRepository;
import com.jjenus.bank.core.shared.Money;
import com.jjenus.bank.core.transfers.Transfer;
import com.jjenus.bank.core.transfers.TransferId;
import com.jjenus.bank.core.transfers.TransferStatus;
import com.jjenus.banking.transactions.infrastructure.TransactionRepository;
import com.jjenus.banking.transfers.application.TransferApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferApplicationService")
class TransferApplicationServiceTest {

    private static final Currency NGN = Currency.getInstance("NGN");

    @Mock AccountRepository accountRepository;
    @Mock TransferRepository transferRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock RedisTemplate<String, String> redis;
    @Mock ValueOperations<String, String> valueOps;

    TransferApplicationService service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        service = new TransferApplicationService(
            accountRepository, transferRepository,
            transactionRepository, eventPublisher, redis);
    }

    // ── executeTransfer ───────────────────────────────────────────────────

    @Test
    @DisplayName("executeTransfer completes, persists transfer and transactions, publishes events")
    void executeTransfer_success() {
        Account from = account("owner-1", "1000.00");
        Account to   = account("owner-2", "0.00");

        when(valueOps.get(any())).thenReturn(null);  // no idempotency cache hit
        when(accountRepository.findById(from.id())).thenReturn(Optional.of(from));
        when(accountRepository.findById(to.id())).thenReturn(Optional.of(to));
        when(accountRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transferRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(valueOps).set(any(), any(), any(Long.class), any(TimeUnit.class));

        Transfer result = service.executeTransfer(
            from.id().value(), to.id().value(),
            "300.00", "NGN", "Test payment", "INV-001", "idem-key-001");

        assertThat(result.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(result.amount().amount()).isEqualByComparingTo("300.0000");

        verify(accountRepository, times(2)).update(any());
        verify(transferRepository).save(any());
        verify(transactionRepository).saveAll(anyList());
        verify(eventPublisher, atLeastOnce()).publishEvent(any());
    }

    @Test
    @DisplayName("executeTransfer with idempotency cache hit returns existing transfer")
    void executeTransfer_idempotencyCacheHit_returnsCached() {
        TransferId existingId = TransferId.generate();
        Transfer existingTransfer = completedTransfer(existingId);

        when(valueOps.get(any())).thenReturn(existingId.value());
        when(transferRepository.getById(existingId)).thenReturn(existingTransfer);

        Transfer result = service.executeTransfer(
            AccountId.generate().value(), AccountId.generate().value(),
            "300.00", "NGN", "Duplicate", "INV-001", "idem-key-001");

        assertThat(result.id()).isEqualTo(existingId);
        verify(accountRepository, never()).findById(any());
        verify(accountRepository, never()).update(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("executeTransfer with insufficient funds throws")
    void executeTransfer_insufficientFunds_throws() {
        Account from = account("owner-1", "50.00");
        Account to   = account("owner-2", "0.00");

        when(valueOps.get(any())).thenReturn(null);
        when(accountRepository.findById(from.id())).thenReturn(Optional.of(from));
        when(accountRepository.findById(to.id())).thenReturn(Optional.of(to));

        assertThatThrownBy(() -> service.executeTransfer(
            from.id().value(), to.id().value(),
            "300.00", "NGN", "Too big", "INV-002", "idem-key-002"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Insufficient");
    }

    @Test
    @DisplayName("executeTransfer with frozen source account throws")
    void executeTransfer_frozenSource_throws() {
        Account frozen = account("owner-1", "1000.00").freeze();
        Account to     = account("owner-2", "0.00");

        when(valueOps.get(any())).thenReturn(null);
        when(accountRepository.findById(frozen.id())).thenReturn(Optional.of(frozen));
        when(accountRepository.findById(to.id())).thenReturn(Optional.of(to));

        assertThatThrownBy(() -> service.executeTransfer(
            frozen.id().value(), to.id().value(),
            "100.00", "NGN", "Payment", "INV-003", "idem-key-003"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("executeTransfer with currency mismatch throws")
    void executeTransfer_currencyMismatch_throws() {
        Account from = accountWithCurrency("owner-1", "1000.00", "USD");
        Account to   = account("owner-2", "0.00"); // NGN

        when(valueOps.get(any())).thenReturn(null);
        when(accountRepository.findById(from.id())).thenReturn(Optional.of(from));
        when(accountRepository.findById(to.id())).thenReturn(Optional.of(to));

        assertThatThrownBy(() -> service.executeTransfer(
            from.id().value(), to.id().value(),
            "100.00", "NGN", "Payment", "INV-004", "idem-key-004"))
            .isInstanceOf(IllegalStateException.class);
    }

    // ── reverseTransfer ───────────────────────────────────────────────────

    @Test
    @DisplayName("reverseTransfer debits receiver and credits sender")
    void reverseTransfer_success() {
        Account sender   = account("sender", "0.00");
        Account receiver = account("receiver", "300.00");
        Transfer completed = completedTransfer(TransferId.generate(), sender.id(), receiver.id());

        when(transferRepository.getById(completed.id())).thenReturn(completed);
        when(accountRepository.getById(completed.toAccountId())).thenReturn(receiver);
        when(accountRepository.getById(completed.fromAccountId())).thenReturn(sender);
        when(accountRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transferRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        Transfer reversed = service.reverseTransfer(completed.id().value(), "Error");

        assertThat(reversed.status()).isEqualTo(TransferStatus.REVERSED);
        verify(eventPublisher, atLeastOnce()).publishEvent(any());
    }

    @Test
    @DisplayName("reverseTransfer on pending transfer throws")
    void reverseTransfer_pending_throws() {
        Account sender   = account("sender", "1000.00");
        Account receiver = account("receiver", "0.00");
        Transfer pending = Transfer.initiate(
            TransferId.generate(), sender.id(), receiver.id(),
            Money.of("100.00", NGN), "Test", "REF-001");

        when(transferRepository.getById(pending.id())).thenReturn(pending);
        when(accountRepository.getById(pending.toAccountId())).thenReturn(receiver);
        when(accountRepository.getById(pending.fromAccountId())).thenReturn(sender);

        assertThatThrownBy(() -> service.reverseTransfer(pending.id().value(), "Reason"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("completed");
    }

    // ── cancelTransfer ────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelTransfer on pending transfer marks it FAILED")
    void cancelTransfer_pending_success() {
        Account sender   = account("sender", "1000.00");
        Account receiver = account("receiver", "0.00");
        Transfer pending = Transfer.initiate(
            TransferId.generate(), sender.id(), receiver.id(),
            Money.of("100.00", NGN), "Test", "REF-001");

        when(transferRepository.getById(pending.id())).thenReturn(pending);
        when(transferRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        Transfer result = service.cancelTransfer(pending.id().value(), "Changed mind");

        assertThat(result.status()).isEqualTo(TransferStatus.FAILED);
        assertThat(result.failureReason()).contains("CANCELLED");
    }

    // ── getTransfersForOwner ──────────────────────────────────────────────

    @Test
    @DisplayName("getTransfersForOwner returns transfers across all owner accounts")
    void getTransfersForOwner_multiAccount() {
        Account a1 = account("owner-1", "500.00");
        Account a2 = account("owner-1", "200.00");
        Transfer t1 = completedTransfer(TransferId.generate(), a1.id(), AccountId.generate());
        Transfer t2 = completedTransfer(TransferId.generate(), a2.id(), AccountId.generate());

        when(accountRepository.findByCustomerId("owner-1")).thenReturn(List.of(a1, a2));
        when(transferRepository.findByAccountId(a1.id())).thenReturn(List.of(t1));
        when(transferRepository.findByAccountId(a2.id())).thenReturn(List.of(t2));

        List<Transfer> results = service.getTransfersForOwner("owner-1");

        assertThat(results).hasSize(2);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Account account(String ownerId, String balance) {
        return accountWithCurrency(ownerId, balance, "NGN");
    }

    private Account accountWithCurrency(String ownerId, String balance, String currencyCode) {
        Currency currency = Currency.getInstance(currencyCode);
        return new Account(
            AccountId.generate(), ownerId,
            Money.of(balance, currency), AccountStatus.ACTIVE,
            Instant.now(), Instant.now(), 0L
        );
    }

    private Transfer completedTransfer(TransferId id) {
        return completedTransfer(id, AccountId.generate(), AccountId.generate());
    }

    private Transfer completedTransfer(TransferId id, AccountId from, AccountId to) {
        return Transfer.initiate(id, from, to, Money.of("300.00", NGN), "Test", "REF-001")
            .markProcessing(null)
            .complete(null);
    }
}

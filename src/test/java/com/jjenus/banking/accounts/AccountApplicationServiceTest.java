package com.jjenus.banking.accounts;

import com.jjenus.bank.core.accounts.Account;
import com.jjenus.bank.core.accounts.AccountId;
import com.jjenus.bank.core.accounts.AccountStatus;
import com.jjenus.bank.core.ports.AccountRepository;
import com.jjenus.bank.core.shared.Money;
import com.jjenus.banking.accounts.application.AccountApplicationService;
import com.jjenus.banking.accounts.infrastructure.AccountOwnerDirectory;
import com.jjenus.banking.transactions.infrastructure.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountApplicationService")
class AccountApplicationServiceTest {

    private static final Currency NGN = Currency.getInstance("NGN");

    @Mock AccountRepository accountRepository;
    @Mock AccountOwnerDirectory ownerDirectory;
    @Mock TransactionRepository transactionRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    AccountApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AccountApplicationService(
            accountRepository, ownerDirectory, transactionRepository, eventPublisher);
    }

    // ── openAccount ───────────────────────────────────────────────────────

    @Test
    @DisplayName("openAccount saves account, records owner name, publishes event")
    void openAccount_savesAndPublishes() {
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Account result = service.openAccount("keycloak-sub-123", "Ada Obi", "NGN");

        assertThat(result.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(result.customerId()).isEqualTo("keycloak-sub-123");
        assertThat(result.balance().amount()).isEqualByComparingTo(BigDecimal.ZERO);

        verify(accountRepository).save(any(Account.class));
        verify(ownerDirectory).recordOwnerName(eq(result.id().value()), eq("Ada Obi"));
        verify(eventPublisher).publishEvent(any());
    }

    // ── deposit ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("deposit updates balance, saves transaction, publishes event")
    void deposit_updatesBalanceAndPublishes() {
        Account account = activeAccount("keycloak-sub-123");
        when(accountRepository.getById(account.id())).thenReturn(account);
        when(accountRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Account result = service.deposit(
            account.id().value(), Money.of("500.00", NGN), "DEP-001");

        assertThat(result.balance().amount()).isEqualByComparingTo("500.0000");
        verify(accountRepository).update(any(Account.class));
        verify(transactionRepository).save(any());
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    @DisplayName("deposit to DORMANT account succeeds")
    void deposit_toDormantAccount_succeeds() {
        Account dormant = activeAccount("owner-1").deposit(Money.of("100.00", NGN)).markDormant();
        when(accountRepository.getById(dormant.id())).thenReturn(dormant);
        when(accountRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Account result = service.deposit(
            dormant.id().value(), Money.of("50.00", NGN), "DEP-002");

        assertThat(result.balance().amount()).isEqualByComparingTo("150.0000");
    }

    @Test
    @DisplayName("deposit to frozen account throws")
    void deposit_toFrozenAccount_throws() {
        Account frozen = activeAccount("owner-1").freeze();
        when(accountRepository.getById(frozen.id())).thenReturn(frozen);

        assertThatThrownBy(() ->
            service.deposit(frozen.id().value(), Money.of("100.00", NGN), "DEP-003"))
            .isInstanceOf(IllegalStateException.class);
    }

    // ── withdraw ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("withdraw updates balance, saves transaction, publishes event")
    void withdraw_updatesBalance() {
        Account withBalance = activeAccount("owner-1").deposit(Money.of("300.00", NGN));
        when(accountRepository.getById(withBalance.id())).thenReturn(withBalance);
        when(accountRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Account result = service.withdraw(
            withBalance.id().value(), Money.of("100.00", NGN), "WITH-001");

        assertThat(result.balance().amount()).isEqualByComparingTo("200.0000");
        verify(transactionRepository).save(any());
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    @DisplayName("withdraw with insufficient funds throws")
    void withdraw_insufficientFunds_throws() {
        Account empty = activeAccount("owner-1");
        when(accountRepository.getById(empty.id())).thenReturn(empty);

        assertThatThrownBy(() ->
            service.withdraw(empty.id().value(), Money.of("100.00", NGN), "WITH-002"))
            .isInstanceOf(IllegalStateException.class);
    }

    // ── freeze / activate / suspend / close ───────────────────────────────

    @Test
    @DisplayName("freezeAccount transitions to FROZEN and publishes event")
    void freezeAccount_transitionsAndPublishes() {
        Account account = activeAccount("owner-1");
        when(accountRepository.getById(account.id())).thenReturn(account);
        when(accountRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        Account result = service.freezeAccount(account.id().value(), "Suspicious");

        assertThat(result.status()).isEqualTo(AccountStatus.FROZEN);
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    @DisplayName("activateAccount transitions frozen account to ACTIVE")
    void activateAccount_fromFrozen_toActive() {
        Account frozen = activeAccount("owner-1").freeze();
        when(accountRepository.getById(frozen.id())).thenReturn(frozen);
        when(accountRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        Account result = service.activateAccount(frozen.id().value());

        assertThat(result.status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("suspendAccount transitions to SUSPENDED")
    void suspendAccount_transitionsToSuspended() {
        Account account = activeAccount("owner-1");
        when(accountRepository.getById(account.id())).thenReturn(account);
        when(accountRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        Account result = service.suspendAccount(account.id().value(), "Regulatory hold");

        assertThat(result.status()).isEqualTo(AccountStatus.SUSPENDED);
    }

    @Test
    @DisplayName("closeAccount with zero balance transitions to CLOSED")
    void closeAccount_zeroBalance_transitions() {
        Account empty = activeAccount("owner-1");
        when(accountRepository.getById(empty.id())).thenReturn(empty);
        when(accountRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        Account result = service.closeAccount(empty.id().value(), "Customer request");

        assertThat(result.status()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    @DisplayName("closeAccount with non-zero balance throws")
    void closeAccount_nonZeroBalance_throws() {
        Account withBalance = activeAccount("owner-1").deposit(Money.of("100.00", NGN));
        when(accountRepository.getById(withBalance.id())).thenReturn(withBalance);

        assertThatThrownBy(() ->
            service.closeAccount(withBalance.id().value(), "Request"))
            .isInstanceOf(IllegalStateException.class);
    }

    // ── AccountQueryApi ───────────────────────────────────────────────────

    @Test
    @DisplayName("getOwnerId returns customerId for existing account")
    void getOwnerId_returnsCustomerId() {
        Account account = activeAccount("keycloak-sub-xyz");
        when(accountRepository.findById(account.id())).thenReturn(Optional.of(account));

        Optional<String> ownerId = service.getOwnerId(account.id().value());

        assertThat(ownerId).hasValue("keycloak-sub-xyz");
    }

    @Test
    @DisplayName("getOwnerId returns empty for non-existent account")
    void getOwnerId_nonExistent_returnsEmpty() {
        when(accountRepository.findById(any())).thenReturn(Optional.empty());

        Optional<String> ownerId = service.getOwnerId(AccountId.generate().value());

        assertThat(ownerId).isEmpty();
    }

    @Test
    @DisplayName("getAccountsForOwner delegates to repository")
    void getAccountsForOwner_delegatesToRepository() {
        Account a1 = activeAccount("owner-1");
        Account a2 = activeAccount("owner-1");
        when(accountRepository.findByCustomerId("owner-1")).thenReturn(List.of(a1, a2));

        List<Account> results = service.getAccountsForOwner("owner-1");

        assertThat(results).hasSize(2);
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private Account activeAccount(String ownerId) {
        return new Account(
            AccountId.generate(), ownerId,
            Money.zero(NGN), AccountStatus.ACTIVE,
            Instant.now(), Instant.now(), 0L
        );
    }
}

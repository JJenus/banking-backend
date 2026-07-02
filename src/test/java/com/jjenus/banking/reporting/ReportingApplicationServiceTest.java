package com.jjenus.banking.reporting;

import com.jjenus.bank.core.accounts.Account;
import com.jjenus.bank.core.accounts.AccountId;
import com.jjenus.bank.core.accounts.AccountStatus;
import com.jjenus.bank.core.ports.AccountRepository;
import com.jjenus.bank.core.shared.Money;
import com.jjenus.bank.core.transactions.Transaction;
import com.jjenus.bank.core.transactions.TransactionId;
import com.jjenus.bank.core.transactions.TransactionType;
import com.jjenus.banking.accounts.AccountQueryApi;
import com.jjenus.banking.ledger.LedgerQueryApi;
import com.jjenus.banking.reporting.application.ReportingApplicationService;
import com.jjenus.banking.reporting.domain.AccountStatement;
import com.jjenus.banking.reporting.domain.TrialBalance;
import com.jjenus.banking.transactions.infrastructure.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportingApplicationService")
class ReportingApplicationServiceTest {

    private static final Currency NGN = Currency.getInstance("NGN");

    @Mock AccountRepository accountRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock LedgerQueryApi ledgerQueryApi;
    @Mock AccountQueryApi accountQueryApi;

    ReportingApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ReportingApplicationService(
            accountRepository, transactionRepository, ledgerQueryApi, accountQueryApi);
    }

    // ── buildStatement ────────────────────────────────────────────────────

    @Test
    @DisplayName("buildStatement assembles opening balance, lines, closing balance")
    void buildStatement_assemblesCorrectly() {
        AccountId accountId = AccountId.generate();
        Account account = activeAccount(accountId, "NGN");
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to   = Instant.parse("2026-01-31T23:59:59Z");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountQueryApi.getOwnerName(accountId.value())).thenReturn(Optional.of("Ada Obi"));
        when(ledgerQueryApi.computeBalanceAsOf(eq(accountId.value()), eq("NGN"), any()))
            .thenReturn(new BigDecimal("1000.00"))
            .thenReturn(new BigDecimal("1500.00"));

        Transaction tx = deposit(accountId, "500.00");
        when(transactionRepository.findByAccountIdInRange(accountId, from, to))
            .thenReturn(List.of(tx));

        AccountStatement statement = service.buildStatement(accountId.value(), from, to);

        assertThat(statement.accountId()).isEqualTo(accountId.value());
        assertThat(statement.ownerName()).isEqualTo("Ada Obi");
        assertThat(statement.currency()).isEqualTo("NGN");
        assertThat(statement.openingBalance()).isEqualByComparingTo("1000.00");
        assertThat(statement.closingBalance()).isEqualByComparingTo("1500.00");
        assertThat(statement.lines()).hasSize(1);

        AccountStatement.StatementLine line = statement.lines().get(0);
        assertThat(line.transactionId()).isEqualTo(tx.id().value());
        assertThat(line.type()).isEqualTo("DEPOSIT");
        assertThat(line.amount()).isEqualByComparingTo("500.0000");
    }

    @Test
    @DisplayName("buildStatement with no transactions returns empty lines but correct balances")
    void buildStatement_noTransactions_emptyLines() {
        AccountId accountId = AccountId.generate();
        Account account = activeAccount(accountId, "NGN");
        Instant from = Instant.now().minusSeconds(3600);
        Instant to   = Instant.now();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountQueryApi.getOwnerName(any())).thenReturn(Optional.empty());
        when(ledgerQueryApi.computeBalanceAsOf(any(), any(), any()))
            .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.findByAccountIdInRange(any(), any(), any()))
            .thenReturn(List.of());

        AccountStatement statement = service.buildStatement(accountId.value(), from, to);

        assertThat(statement.lines()).isEmpty();
        assertThat(statement.ownerName()).isEqualTo("Unknown");
    }

    @Test
    @DisplayName("buildStatement for non-existent account throws ResourceNotFoundException")
    void buildStatement_accountNotFound_throws() {
        when(accountRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buildStatement(
            AccountId.generate().value(),
            Instant.now().minusSeconds(86400),
            Instant.now()))
            .isInstanceOf(com.jjenus.banking.shared.exception.ResourceNotFoundException.class);
    }

    // ── buildTrialBalance ─────────────────────────────────────────────────

    @Test
    @DisplayName("buildTrialBalance sums debits and credits per account")
    void buildTrialBalance_sumsCorrectly() {
        AccountId customer = AccountId.generate();
        Instant asOf = Instant.now();

        List<LedgerQueryApi.LedgerEntryView> entries = List.of(
            entry("ACC-SYSCASH001", customer.value(), new BigDecimal("500.00")),
            entry("ACC-SYSCASH001", customer.value(), new BigDecimal("200.00")),
            entry(customer.value(), "ACC-SYSCASH001", new BigDecimal("100.00"))
        );

        when(ledgerQueryApi.getAllEntriesAsOf("NGN", asOf)).thenReturn(entries);
        when(accountQueryApi.getOwnerName(customer.value())).thenReturn(Optional.of("Ada Obi"));

        TrialBalance balance = service.buildTrialBalance("NGN", asOf);

        assertThat(balance.currency()).isEqualTo("NGN");
        assertThat(balance.isBalanced()).isTrue();
        // totalDebits == totalCredits (800 each, since every debit has a matching credit)
        assertThat(balance.totalDebits()).isEqualByComparingTo(balance.totalCredits());
        assertThat(balance.lines()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("buildTrialBalance with no entries returns empty balanced result")
    void buildTrialBalance_noEntries_emptyBalanced() {
        when(ledgerQueryApi.getAllEntriesAsOf(any(), any())).thenReturn(List.of());

        TrialBalance balance = service.buildTrialBalance("NGN", Instant.now());

        assertThat(balance.lines()).isEmpty();
        assertThat(balance.totalDebits()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance.totalCredits()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance.isBalanced()).isTrue();
    }

    @Test
    @DisplayName("buildTrialBalance labels system accounts correctly")
    void buildTrialBalance_labelsSystemAccounts() {
        Instant asOf = Instant.now();
        AccountId customer = AccountId.generate();

        when(ledgerQueryApi.getAllEntriesAsOf("NGN", asOf)).thenReturn(List.of(
            entry("ACC-SYSCASH001", customer.value(), new BigDecimal("100.00"))
        ));
        when(accountQueryApi.getOwnerName(customer.value())).thenReturn(Optional.of("Test User"));

        TrialBalance balance = service.buildTrialBalance("NGN", asOf);

        boolean hasCashLine = balance.lines().stream()
            .anyMatch(l -> l.accountLabel().contains("Cash"));
        assertThat(hasCashLine).isTrue();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Account activeAccount(AccountId id, String currency) {
        return new Account(id, "owner-sub", Money.zero(Currency.getInstance(currency)),
            AccountStatus.ACTIVE, Instant.now(), Instant.now(), 0L);
    }

    private Transaction deposit(AccountId accountId, String amount) {
        return Transaction.createDeposit(
            TransactionId.generate(), accountId,
            Money.of(amount, NGN), Money.of(amount, NGN), "DEP-001");
    }

    private LedgerQueryApi.LedgerEntryView entry(String debitId, String creditId, BigDecimal amount) {
        return new LedgerQueryApi.LedgerEntryView(
            "JNL-" + java.util.UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(),
            debitId, creditId, amount, "NGN",
            "Test entry", "REF-001", "SRC-001", Instant.now()
        );
    }
}

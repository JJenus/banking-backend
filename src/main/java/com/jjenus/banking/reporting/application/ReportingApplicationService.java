package com.jjenus.banking.reporting.application;

import com.jjenus.bank.core.accounts.Account;
import com.jjenus.bank.core.accounts.AccountId;
import com.jjenus.bank.core.ports.AccountRepository;
import com.jjenus.bank.core.transactions.Transaction;
import com.jjenus.banking.accounts.AccountQueryApi;
import com.jjenus.banking.ledger.LedgerQueryApi;
import com.jjenus.banking.reporting.domain.AccountStatement;
import com.jjenus.banking.reporting.domain.TrialBalance;
import com.jjenus.banking.shared.exception.ResourceNotFoundException;
import com.jjenus.banking.transactions.infrastructure.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Application service for the reporting module.
 *
 * <p>Assembles read models ({@link AccountStatement}, {@link TrialBalance})
 * from data across three sources:
 * <ul>
 *   <li>{@code accounts} module — via {@link AccountQueryApi} (owner names)</li>
 *   <li>{@code transactions} table — for statement lines</li>
 *   <li>{@code ledger} module — via {@link LedgerQueryApi} (balances, entries)</li>
 * </ul>
 *
 * <p>All methods are read-only.
 */
@Service
@Transactional(readOnly = true)
public class ReportingApplicationService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerQueryApi ledgerQueryApi;
    private final AccountQueryApi accountQueryApi;

    public ReportingApplicationService(AccountRepository accountRepository,
                                       TransactionRepository transactionRepository,
                                       LedgerQueryApi ledgerQueryApi,
                                       AccountQueryApi accountQueryApi) {
        this.accountRepository     = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerQueryApi        = ledgerQueryApi;
        this.accountQueryApi       = accountQueryApi;
    }

    // ── Account Statement ─────────────────────────────────────────────────

    public AccountStatement buildStatement(String accountId, Instant periodFrom, Instant periodTo) {
        Account account = accountRepository.findById(AccountId.of(accountId))
            .orElseThrow(() -> ResourceNotFoundException.of("Account", accountId));

        String ownerName = accountQueryApi.getOwnerName(accountId).orElse("Unknown");
        String currency  = account.balance().currency().getCurrencyCode();

        BigDecimal openingBalance = ledgerQueryApi.computeBalanceAsOf(
            accountId, currency, periodFrom.minusMillis(1));

        List<Transaction> txns = transactionRepository
            .findByAccountIdInRange(AccountId.of(accountId), periodFrom, periodTo);

        List<AccountStatement.StatementLine> lines = txns.stream()
            .map(tx -> new AccountStatement.StatementLine(
                tx.id().value(),
                tx.timestamp(),
                tx.type().name(),
                tx.amount().amount(),
                tx.balanceAfter().amount(),
                tx.description(),
                tx.reference()
            ))
            .collect(Collectors.toList());

        BigDecimal closingBalance = ledgerQueryApi.computeBalanceAsOf(accountId, currency, periodTo);

        return new AccountStatement(
            accountId, ownerName, currency,
            periodFrom, periodTo,
            openingBalance, closingBalance,
            lines, Instant.now()
        );
    }

    public byte[] buildStatementPdf(String accountId, Instant periodFrom, Instant periodTo,
                                    StatementPdfGenerator generator) {
        return generator.generate(buildStatement(accountId, periodFrom, periodTo));
    }

    // ── Trial Balance ─────────────────────────────────────────────────────

    public TrialBalance buildTrialBalance(String currency, Instant asOf) {
        // Fetch all ledger entries up to asOf for this currency
        List<LedgerQueryApi.LedgerEntryView> allEntries =
            ledgerQueryApi.getAllEntriesAsOf(currency, asOf);

        // Collect all unique account IDs that appear in the ledger
        Set<String> accountIds = new LinkedHashSet<>();
        for (LedgerQueryApi.LedgerEntryView e : allEntries) {
            accountIds.add(e.debitAccountId());
            accountIds.add(e.creditAccountId());
        }

        // Per-account debit and credit totals
        Map<String, BigDecimal> debits  = new HashMap<>();
        Map<String, BigDecimal> credits = new HashMap<>();

        for (LedgerQueryApi.LedgerEntryView entry : allEntries) {
            debits.merge(entry.debitAccountId(),   entry.amount(), BigDecimal::add);
            credits.merge(entry.creditAccountId(), entry.amount(), BigDecimal::add);
        }

        List<TrialBalance.AccountBalanceLine> lines = new ArrayList<>();
        BigDecimal totalDebits  = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (String accId : accountIds) {
            BigDecimal debitTotal  = debits.getOrDefault(accId, BigDecimal.ZERO);
            BigDecimal creditTotal = credits.getOrDefault(accId, BigDecimal.ZERO);
            BigDecimal net         = creditTotal.subtract(debitTotal);
            String label           = resolveLabel(accId);

            lines.add(new TrialBalance.AccountBalanceLine(accId, label, debitTotal, creditTotal, net));
            totalDebits  = totalDebits.add(debitTotal);
            totalCredits = totalCredits.add(creditTotal);
        }

        return new TrialBalance(currency, asOf, lines, totalDebits, totalCredits, Instant.now());
    }

    private String resolveLabel(String accountId) {
        return switch (accountId) {
            case "ACC-SYSCASH001"  -> "Cash / Vault (System)";
            case "ACC-SYSFEEINC1" -> "Fee Income (System)";
            case "ACC-SYSINTEXP1" -> "Interest Expense (System)";
            default -> accountQueryApi.getOwnerName(accountId).orElse(accountId);
        };
    }
}

package com.jjenus.banking.ledger.application;

import com.jjenus.bank.core.accounts.AccountId;
import com.jjenus.bank.core.ledger.LedgerEntry;
import com.jjenus.bank.core.ports.LedgerRepository;
import com.jjenus.banking.ledger.LedgerQueryApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;

/**
 * Application service for the ledger module.
 *
 * <p>Implements {@link LedgerQueryApi} — the public interface other modules
 * (primarily {@code reporting}) use to read ledger data.
 *
 * <p>This service is read-only. All writes to the ledger happen exclusively
 * in {@link LedgerListener}, in response to domain events. There is no
 * "post a manual ledger entry" endpoint — every entry traces back to a
 * specific account or transfer event, by design, to preserve the audit trail.
 */
@Service
@Transactional(readOnly = true)
public class LedgerApplicationService implements LedgerQueryApi {

    private final LedgerRepository ledgerRepository;

    public LedgerApplicationService(LedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @Override
    public BigDecimal computeBalance(String accountId, String currencyCode) {
        Currency currency = Currency.getInstance(currencyCode);
        return ledgerRepository.computeBalance(AccountId.of(accountId), currency).amount();
    }

    @Override
    public BigDecimal computeBalanceAsOf(String accountId, String currencyCode, Instant asOf) {
        Currency currency = Currency.getInstance(currencyCode);
        return ledgerRepository.computeBalanceAsOf(AccountId.of(accountId), currency, asOf).amount();
    }

    @Override
    public List<LedgerEntryView> getEntriesForAccount(String accountId) {
        return ledgerRepository.findByAccountId(AccountId.of(accountId))
            .stream()
            .map(this::toView)
            .toList();
    }

    @Override
    public List<LedgerEntryView> getEntriesForAccountInRange(String accountId, Instant from, Instant to) {
        return ledgerRepository.findByAccountId(AccountId.of(accountId))
            .stream()
            .filter(e -> !e.postedAt().isBefore(from) && !e.postedAt().isAfter(to))
            .map(this::toView)
            .toList();
    }

    /**
     * Returns all ledger entries for an account — used by the ledger module's
     * own admin endpoint. Returns the bank-core domain type directly since
     * this method is only called from within the ledger module's own
     * controller, not across a module boundary.
     */
    public List<LedgerEntry> getRawEntriesForAccount(String accountId) {
        return ledgerRepository.findByAccountId(AccountId.of(accountId));
    }

    private LedgerEntryView toView(LedgerEntry entry) {
        return new LedgerEntryView(
            entry.id().value(),
            entry.debitAccountId().value(),
            entry.creditAccountId().value(),
            entry.amount().amount(),
            entry.amount().currency().getCurrencyCode(),
            entry.description(),
            entry.reference(),
            entry.sourceId(),
            entry.postedAt()
        );
    }
}

package com.jjenus.banking.ledger.application;

import com.jjenus.bank.core.accounts.AccountId;
import com.jjenus.bank.core.ledger.LedgerEntry;
import com.jjenus.bank.core.ports.LedgerRepository;
import com.jjenus.banking.ledger.LedgerQueryApi;
import com.jjenus.banking.ledger.infrastructure.LedgerEntryJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class LedgerApplicationService implements LedgerQueryApi {

    private final LedgerRepository ledgerRepository;
    private final LedgerEntryJpaRepository jpaRepository;

    public LedgerApplicationService(LedgerRepository ledgerRepository,
                                    LedgerEntryJpaRepository jpaRepository) {
        this.ledgerRepository = ledgerRepository;
        this.jpaRepository    = jpaRepository;
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
            .stream().map(this::toView).toList();
    }

    @Override
    public List<LedgerEntryView> getEntriesForAccountInRange(String accountId, Instant from, Instant to) {
        return jpaRepository.findByAccountIdAndPostedAtBetween(accountId, from, to)
            .stream()
            .map(e -> new LedgerEntryView(
                e.getId(), e.getDebitAccountId(), e.getCreditAccountId(),
                e.getAmount(), e.getCurrency(), e.getDescription(),
                e.getReference(), e.getSourceId(), e.getPostedAt()))
            .toList();
    }

    @Override
    public List<LedgerEntryView> getAllEntriesAsOf(String currencyCode, Instant asOf) {
        return jpaRepository.findAllByCurrencyAndPostedAtBefore(currencyCode, asOf)
            .stream()
            .map(e -> new LedgerEntryView(
                e.getId(), e.getDebitAccountId(), e.getCreditAccountId(),
                e.getAmount(), e.getCurrency(), e.getDescription(),
                e.getReference(), e.getSourceId(), e.getPostedAt()))
            .toList();
    }

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

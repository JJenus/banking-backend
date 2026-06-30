package com.jjenus.banking.ledger.infrastructure;

import com.jjenus.bank.core.accounts.AccountId;
import com.jjenus.bank.core.ledger.LedgerEntry;
import com.jjenus.bank.core.ledger.LedgerEntryId;
import com.jjenus.bank.core.ports.LedgerRepository;
import com.jjenus.bank.core.shared.Money;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Infrastructure adapter implementing the {@link LedgerRepository} port
 * defined in bank-core. Bridges between the domain's immutable
 * {@link LedgerEntry} record and the JPA entity {@link LedgerEntryJpaEntity}.
 *
 * <p>This table is append-only. {@link #post(LedgerEntry)} and
 * {@link #postAll(List)} are the only write operations exposed — there is no
 * update or delete method on this class, by design.
 */
@Repository
class LedgerRepositoryAdapter implements LedgerRepository {

    private final LedgerEntryJpaRepository jpa;

    LedgerRepositoryAdapter(LedgerEntryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public LedgerEntry post(LedgerEntry entry) {
        LedgerEntryJpaEntity entity = toEntity(entry);
        jpa.save(entity);
        return entry;
    }

    @Override
    public List<LedgerEntry> postAll(List<LedgerEntry> entries) {
        List<LedgerEntryJpaEntity> entities = entries.stream()
            .map(this::toEntity)
            .collect(Collectors.toList());
        jpa.saveAll(entities);
        return entries;
    }

    @Override
    public Optional<LedgerEntry> findById(LedgerEntryId id) {
        return jpa.findById(id.value()).map(this::toDomain);
    }

    @Override
    public List<LedgerEntry> findByAccountId(AccountId accountId) {
        return jpa.findByAccountId(accountId.value())
            .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<LedgerEntry> findByAccountIdAsOf(AccountId accountId, Instant asOf) {
        return jpa.findByAccountIdAsOf(accountId.value(), asOf)
            .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<LedgerEntry> findByReference(String reference) {
        return jpa.findByReference(reference)
            .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public Money computeBalance(AccountId accountId, Currency currency) {
        java.math.BigDecimal total = jpa.computeBalance(accountId.value(), currency.getCurrencyCode());
        return Money.of(total.toPlainString(), currency);
    }

    @Override
    public Money computeBalanceAsOf(AccountId accountId, Currency currency, Instant asOf) {
        java.math.BigDecimal total = jpa.computeBalanceAsOf(
            accountId.value(), currency.getCurrencyCode(), asOf);
        return Money.of(total.toPlainString(), currency);
    }

    @Override
    public boolean existsByReferenceAndSource(String reference, String sourceId) {
        return jpa.existsByReferenceAndSourceId(reference, sourceId);
    }

    @Override
    public long count() {
        return jpa.count();
    }

    // ── Mapping ───────────────────────────────────────────────────────────

    private LedgerEntryJpaEntity toEntity(LedgerEntry entry) {
        return new LedgerEntryJpaEntity(
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

    private LedgerEntry toDomain(LedgerEntryJpaEntity entity) {
        Currency currency = Currency.getInstance(entity.getCurrency());
        return new LedgerEntry(
            LedgerEntryId.of(entity.getId()),
            AccountId.of(entity.getDebitAccountId()),
            AccountId.of(entity.getCreditAccountId()),
            Money.of(entity.getAmount().toPlainString(), currency),
            entity.getDescription(),
            entity.getReference(),
            entity.getSourceId(),
            entity.getPostedAt()
        );
    }
}

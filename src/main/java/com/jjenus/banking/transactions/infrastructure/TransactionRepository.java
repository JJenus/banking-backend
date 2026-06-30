package com.jjenus.banking.transactions.infrastructure;

import com.jjenus.bank.core.accounts.AccountId;
import com.jjenus.bank.core.transactions.Transaction;
import com.jjenus.bank.core.transactions.TransactionId;
import com.jjenus.bank.core.transactions.TransactionType;
import com.jjenus.bank.core.shared.Money;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Spring repository for {@link Transaction} persistence.
 *
 * <p>Note: bank-core does not define a {@code TransactionRepository} port —
 * transactions are a derived read model (statement lines) rather than an
 * aggregate root. They are persisted by {@code TransferApplicationService}
 * and {@code AccountApplicationService} inline with the transfer/deposit
 * operation, not through a separate port adapter.
 *
 * <p>This class is accessible within the {@code transactions} sub-package
 * and exposed via {@link TransactionQueryApi} to other modules.
 */
@Repository
public class TransactionRepository {

    private final TransactionJpaRepository jpa;

    public TransactionRepository(TransactionJpaRepository jpa) {
        this.jpa = jpa;
    }

    public Transaction save(Transaction transaction) {
        jpa.save(toEntity(transaction));
        return transaction;
    }

    public List<Transaction> saveAll(List<Transaction> transactions) {
        jpa.saveAll(transactions.stream().map(this::toEntity).collect(Collectors.toList()));
        return transactions;
    }

    public Optional<Transaction> findById(TransactionId id) {
        return jpa.findById(id.value()).map(this::toDomain);
    }

    public Page<Transaction> findByAccountIdPaged(AccountId accountId, Pageable pageable) {
        return jpa.findByAccountIdPaged(accountId.value(), pageable).map(this::toDomain);
    }

    public List<Transaction> findByAccountId(AccountId accountId) {
        return jpa.findByAccountId(accountId.value())
            .stream().map(this::toDomain).collect(Collectors.toList());
    }

    public List<Transaction> findByAccountIdInRange(AccountId accountId, Instant from, Instant to) {
        return jpa.findByAccountIdAndTimestampBetween(accountId.value(), from, to)
            .stream().map(this::toDomain).collect(Collectors.toList());
    }

    // ── Mapping ───────────────────────────────────────────────────────────

    private TransactionJpaEntity toEntity(Transaction t) {
        return new TransactionJpaEntity(
            t.id().value(),
            t.accountId().value(),
            t.type().name(),
            t.amount().amount(),
            t.amount().currency().getCurrencyCode(),
            t.balanceAfter().amount(),
            t.description(),
            t.reference(),
            t.relatedTransactionId(),
            t.metadata(),
            t.timestamp()
        );
    }

    private Transaction toDomain(TransactionJpaEntity e) {
        Currency currency = Currency.getInstance(e.getCurrency());
        return new Transaction(
            TransactionId.of(e.getId()),
            AccountId.of(e.getAccountId()),
            TransactionType.valueOf(e.getType()),
            Money.of(e.getAmount().toPlainString(), currency),
            Money.of(e.getBalanceAfter().toPlainString(), currency),
            e.getDescription(),
            e.getReference(),
            e.getTimestamp(),
            e.getLinkedTxId(),
            e.getMetadata()
        );
    }
}

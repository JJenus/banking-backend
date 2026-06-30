package com.jjenus.banking.transfers.infrastructure;

import com.jjenus.bank.core.accounts.AccountId;
import com.jjenus.bank.core.ports.TransferRepository;
import com.jjenus.bank.core.shared.Money;
import com.jjenus.bank.core.transactions.TransactionId;
import com.jjenus.bank.core.transfers.Transfer;
import com.jjenus.bank.core.transfers.TransferId;
import com.jjenus.bank.core.transfers.TransferStatus;
import org.springframework.stereotype.Repository;

import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Infrastructure adapter implementing bank-core's {@link TransferRepository} port.
 */
@Repository
class TransferRepositoryAdapter implements TransferRepository {

    private final TransferJpaRepository jpa;

    TransferRepositoryAdapter(TransferJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Transfer save(Transfer transfer) {
        jpa.save(toEntity(transfer));
        return transfer;
    }

    @Override
    public Transfer update(Transfer transfer) {
        TransferJpaEntity entity = jpa.findById(transfer.id().value())
            .orElseThrow(() -> new IllegalArgumentException(
                "Transfer not found for update: " + transfer.id().value()));

        entity.setStatus(transfer.status().name());
        entity.setDebitTransactionId(transfer.debitTransactionId() != null
            ? transfer.debitTransactionId().value() : null);
        entity.setCreditTransactionId(transfer.creditTransactionId() != null
            ? transfer.creditTransactionId().value() : null);
        entity.setFailureReason(transfer.failureReason());
        entity.setCompletedAt(transfer.completedAt());
        jpa.save(entity);
        return transfer;
    }

    @Override
    public Optional<Transfer> findById(TransferId id) {
        return jpa.findById(id.value()).map(this::toDomain);
    }

    @Override
    public List<Transfer> findByFromAccountId(AccountId accountId) {
        return jpa.findByFromAccountIdOrderByCreatedAtDesc(accountId.value())
            .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Transfer> findByToAccountId(AccountId accountId) {
        return jpa.findByToAccountIdOrderByCreatedAtDesc(accountId.value())
            .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Transfer> findByAccountId(AccountId accountId) {
        return jpa.findByAccountId(accountId.value())
            .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Transfer> findByStatus(TransferStatus status) {
        return jpa.findByStatusOrderByCreatedAtDesc(status.name())
            .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Transfer> findByAccountIdAndDateRange(AccountId accountId,
                                                      java.time.Instant from,
                                                      java.time.Instant to) {
        return jpa.findByAccountIdAndDateRange(accountId.value(), from, to)
            .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public boolean existsByReference(String reference) {
        return jpa.existsByReference(reference);
    }

    @Override
    public long count() {
        return jpa.count();
    }

    // ── Mapping ───────────────────────────────────────────────────────────

    private TransferJpaEntity toEntity(Transfer t) {
        TransferJpaEntity entity = new TransferJpaEntity(
            t.id().value(),
            t.fromAccountId().value(),
            t.toAccountId().value(),
            t.amount().amount(),
            t.amount().currency().getCurrencyCode(),
            t.description(),
            t.reference(),
            t.status().name(),
            t.createdAt()
        );
        entity.setDebitTransactionId(t.debitTransactionId() != null
            ? t.debitTransactionId().value() : null);
        entity.setCreditTransactionId(t.creditTransactionId() != null
            ? t.creditTransactionId().value() : null);
        entity.setFailureReason(t.failureReason());
        entity.setCompletedAt(t.completedAt());
        return entity;
    }

    private Transfer toDomain(TransferJpaEntity e) {
        Currency currency = Currency.getInstance(e.getCurrency());
        return new Transfer(
            TransferId.of(e.getId()),
            AccountId.of(e.getFromAccountId()),
            AccountId.of(e.getToAccountId()),
            Money.of(e.getAmount().toPlainString(), currency),
            TransferStatus.valueOf(e.getStatus()),
            e.getDescription(),
            e.getReference(),
            e.getCreatedAt(),
            e.getCompletedAt(),
            e.getDebitTransactionId() != null
                ? TransactionId.of(e.getDebitTransactionId()) : null,
            e.getCreditTransactionId() != null
                ? TransactionId.of(e.getCreditTransactionId()) : null,
            e.getFailureReason()
        );
    }
}

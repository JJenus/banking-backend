package com.jjenus.banking.accounts.infrastructure;

import com.jjenus.bank.core.accounts.Account;
import com.jjenus.bank.core.accounts.AccountId;
import com.jjenus.bank.core.accounts.AccountStatus;
import com.jjenus.bank.core.ports.AccountRepository;
import com.jjenus.bank.core.shared.Money;
import org.springframework.stereotype.Repository;

import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Infrastructure adapter implementing the {@link AccountRepository} port
 * defined in bank-core. Bridges between the domain's immutable {@link Account}
 * record and the JPA entity {@link AccountJpaEntity}.
 *
 * <p>This class is the only place in the codebase where domain objects are
 * converted to/from JPA entities for accounts.
 *
 * <p><b>Important:</b> bank-core's {@code Account.customerId()} stores the
 * Keycloak owner ID (not a display name) so that
 * {@link #findByCustomerId(String)} correctly resolves "my accounts" queries.
 * The human-readable owner name is set separately via
 * {@link AccountOwnerDirectory} immediately after {@link #save(Account)} is
 * called by {@code AccountApplicationService.openAccount()} — it is never
 * derived from the domain object here. On {@link #save(Account)}, the
 * {@code owner_name} column is initialised to the owner ID as a safe
 * placeholder until {@code AccountOwnerDirectory.recordOwnerName()} overwrites
 * it in the same transaction.
 */
@Repository
class AccountRepositoryAdapter implements AccountRepository {

    private final AccountJpaRepository jpa;

    AccountRepositoryAdapter(AccountJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Account save(Account account) {
        AccountJpaEntity entity = toEntity(account);
        jpa.save(entity);
        return account;
    }

    @Override
    public Account update(Account account) {
        AccountJpaEntity entity = jpa.findById(account.id().value())
            .orElseThrow(() -> new IllegalArgumentException(
                "Account not found for update: " + account.id().value()));

        // owner_name is intentionally NOT touched here — it is independent
        // JPA-layer metadata managed by AccountOwnerDirectory, not part of
        // the bank-core domain object being persisted.
        entity.setBalance(account.balance().amount());
        entity.setStatus(account.status().name());
        entity.setVersion(account.version());
        entity.setLastUpdatedAt(account.lastUpdatedAt());
        jpa.save(entity);
        return account;
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return jpa.findById(id.value()).map(this::toDomain);
    }

    @Override
    public List<Account> findByCustomerId(String customerId) {
        return jpa.findByOwnerId(customerId)
            .stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public boolean existsById(AccountId id) {
        return jpa.existsById(id.value());
    }

    @Override
    public long count() {
        return jpa.count();
    }

    // ── Mapping ───────────────────────────────────────────────────────────

    private AccountJpaEntity toEntity(Account account) {
        return new AccountJpaEntity(
            account.id().value(),
            account.customerId(),         // ownerId = Keycloak sub
            account.customerId(),         // owner_name placeholder — overwritten by
                                           // AccountOwnerDirectory.recordOwnerName() in the
                                           // same transaction during account creation
            account.balance().amount(),
            account.balance().currency().getCurrencyCode(),
            account.status().name(),
            account.version(),
            account.createdAt(),
            account.lastUpdatedAt()
        );
    }

    private Account toDomain(AccountJpaEntity entity) {
        Currency currency = Currency.getInstance(entity.getCurrency());
        return new Account(
            AccountId.of(entity.getId()),
            entity.getOwnerId(),
            Money.of(entity.getBalance().toPlainString(), currency),
            AccountStatus.valueOf(entity.getStatus()),
            entity.getCreatedAt(),
            entity.getLastUpdatedAt(),
            entity.getVersion()
        );
    }
}

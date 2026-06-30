package com.jjenus.banking.accounts.application;

import com.jjenus.bank.core.accounts.*;
import com.jjenus.bank.core.ports.AccountRepository;
import com.jjenus.bank.core.shared.Money;
import com.jjenus.bank.core.shared.Result;
import com.jjenus.banking.accounts.AccountQueryApi;
import com.jjenus.banking.accounts.infrastructure.AccountOwnerDirectory;
import com.jjenus.banking.shared.exception.ResourceNotFoundException;
import com.jjenus.banking.transactions.infrastructure.TransactionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Application service for account operations.
 *
 * <p>Orchestrates the command → domain service → repository → event publish flow.
 * Never contains business logic — delegates entirely to bank-core's {@link AccountService}.
 *
 * <p>Every method is transactional. Domain events are published <em>after</em>
 * the transaction commits via Spring Modulith's transactional event support.
 *
 * <p>Also implements {@link AccountQueryApi} — the public interface other
 * modules use to query account data without crossing module boundaries.
 *
 * <p><b>Owner ID vs owner name:</b> bank-core's {@code Account.customerId()} is a
 * single string field. This application maps it to the Keycloak {@code sub}
 * (the stable owner ID), since that's what's needed for "find my accounts"
 * queries and for resolving notification recipients. The human-readable owner
 * name is tracked separately by {@link AccountOwnerDirectory} in the
 * infrastructure layer — it is metadata for display purposes only and never
 * flows through bank-core.
 */
@Service
@Transactional
public class AccountApplicationService implements AccountQueryApi {

    private final AccountRepository accountRepository;
    private final AccountOwnerDirectory ownerDirectory;
    private final TransactionRepository transactionRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AccountApplicationService(AccountRepository accountRepository,
                                     AccountOwnerDirectory ownerDirectory,
                                     TransactionRepository transactionRepository,
                                     ApplicationEventPublisher eventPublisher) {
        this.accountRepository      = accountRepository;
        this.ownerDirectory         = ownerDirectory;
        this.transactionRepository  = transactionRepository;
        this.eventPublisher         = eventPublisher;
    }

    /**
     * Opens a new account for a customer.
     *
     * @param ownerId      Keycloak sub of the authenticated user — stored as
     *                     bank-core's {@code customerId} so balance lookups by
     *                     owner work correctly
     * @param ownerName    display name of the account owner, tracked alongside
     *                     the account for display purposes
     * @param currencyCode ISO 4217 currency code (e.g. "NGN", "USD")
     * @return the newly created account
     */
    public Account openAccount(String ownerId, String ownerName, String currencyCode) {
        AccountId accountId = AccountId.generate();

        // bank-core's CreateAccount.ownerName parameter becomes Account.customerId().
        // We pass the Keycloak ownerId here so that AccountRepository.findByCustomerId(ownerId)
        // correctly returns this account.
        AccountCommand.CreateAccount command =
            AccountCommand.CreateAccount.now(accountId, ownerId, currencyCode);

        Result<AccountService.AccountCreationResult> result =
            AccountService.createAccount(command);

        if (result.isFailure()) {
            throw new IllegalArgumentException("Failed to create account: " + result.getErrorOrNull());
        }

        AccountService.AccountCreationResult creation = result.getOrThrow();
        accountRepository.save(creation.account());

        // Track the human-readable owner name alongside the account —
        // this is JPA-layer metadata, not a bank-core domain concern.
        ownerDirectory.recordOwnerName(accountId.value(), ownerName);

        eventPublisher.publishEvent(creation.event());

        return creation.account();
    }

    /**
     * Deposits money into an account.
     */
    public Account deposit(String accountId, Money amount, String reference) {
        Account account = accountRepository.getById(AccountId.of(accountId));

        AccountCommand.DepositMoney command =
            AccountCommand.DepositMoney.now(account.id(), amount, reference);

        Result<AccountService.DepositResult> result = AccountService.deposit(account, command);

        if (result.isFailure()) {
            throw new IllegalStateException(result.getErrorOrNull());
        }

        AccountService.DepositResult deposit = result.getOrThrow();
        accountRepository.update(deposit.updatedAccount());
        transactionRepository.save(deposit.transaction());
        eventPublisher.publishEvent(deposit.event());

        return deposit.updatedAccount();
    }

    /**
     * Withdraws money from an account.
     */
    public Account withdraw(String accountId, Money amount, String reference) {
        Account account = accountRepository.getById(AccountId.of(accountId));

        AccountCommand.WithdrawMoney command =
            AccountCommand.WithdrawMoney.now(account.id(), amount, reference);

        Result<AccountService.WithdrawalResult> result = AccountService.withdraw(account, command);

        if (result.isFailure()) {
            throw new IllegalStateException(result.getErrorOrNull());
        }

        AccountService.WithdrawalResult withdrawal = result.getOrThrow();
        accountRepository.update(withdrawal.updatedAccount());
        transactionRepository.save(withdrawal.transaction());
        eventPublisher.publishEvent(withdrawal.event());

        return withdrawal.updatedAccount();
    }

    /**
     * Freezes an account.
     */
    public Account freezeAccount(String accountId, String reason) {
        Account account = accountRepository.getById(AccountId.of(accountId));
        AccountCommand.FreezeAccount command = AccountCommand.FreezeAccount.now(account.id(), reason);

        Result<AccountService.AccountStatusChangeResult> result = AccountService.freeze(account, command);
        if (result.isFailure()) throw new IllegalStateException(result.getErrorOrNull());

        AccountService.AccountStatusChangeResult change = result.getOrThrow();
        accountRepository.update(change.updatedAccount());
        eventPublisher.publishEvent(change.event());
        return change.updatedAccount();
    }

    /**
     * Activates a frozen, suspended, or dormant account.
     */
    public Account activateAccount(String accountId) {
        Account account = accountRepository.getById(AccountId.of(accountId));
        AccountCommand.ActivateAccount command = AccountCommand.ActivateAccount.now(account.id());

        Result<AccountService.AccountStatusChangeResult> result = AccountService.activate(account, command);
        if (result.isFailure()) throw new IllegalStateException(result.getErrorOrNull());

        AccountService.AccountStatusChangeResult change = result.getOrThrow();
        accountRepository.update(change.updatedAccount());
        eventPublisher.publishEvent(change.event());
        return change.updatedAccount();
    }

    /**
     * Closes an account (only possible with zero balance).
     */
    public Account closeAccount(String accountId, String reason) {
        Account account = accountRepository.getById(AccountId.of(accountId));
        AccountCommand.CloseAccount command = AccountCommand.CloseAccount.now(account.id(), reason);

        Result<AccountService.AccountStatusChangeResult> result = AccountService.closeAccount(account, command);
        if (result.isFailure()) throw new IllegalStateException(result.getErrorOrNull());

        AccountService.AccountStatusChangeResult change = result.getOrThrow();
        accountRepository.update(change.updatedAccount());
        eventPublisher.publishEvent(change.event());
        return change.updatedAccount();
    }

    // ── Queries ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Account getAccount(String accountId) {
        return accountRepository.findById(AccountId.of(accountId))
            .orElseThrow(() -> ResourceNotFoundException.of("Account", accountId));
    }

    @Transactional(readOnly = true)
    public List<Account> getAccountsForOwner(String ownerId) {
        return accountRepository.findByCustomerId(ownerId);
    }

    // ── AccountQueryApi implementation (used by other modules) ───────────

    @Override
    @Transactional(readOnly = true)
    public Optional<String> getOwnerId(String accountId) {
        return accountRepository.findById(AccountId.of(accountId))
            .map(Account::customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> getOwnerName(String accountId) {
        return ownerDirectory.getOwnerName(accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean accountExists(String accountId) {
        try {
            return accountRepository.existsById(AccountId.of(accountId));
        } catch (IllegalArgumentException invalidFormat) {
            return false;
        }
    }
}

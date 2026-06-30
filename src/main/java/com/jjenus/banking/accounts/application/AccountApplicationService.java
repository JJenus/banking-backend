package com.jjenus.banking.accounts.application;

import com.jjenus.bank.core.accounts.*;
import com.jjenus.bank.core.ports.AccountRepository;
import com.jjenus.bank.core.shared.Money;
import com.jjenus.bank.core.shared.Result;
import com.jjenus.banking.shared.exception.ResourceNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.List;

/**
 * Application service for account operations.
 *
 * <p>Orchestrates the command → domain service → repository → event publish flow.
 * Never contains business logic — delegates entirely to bank-core's {@link AccountService}.
 *
 * <p>Every method is transactional. Domain events are published <em>after</em>
 * the transaction commits via Spring Modulith's transactional event support.
 */
@Service
@Transactional
public class AccountApplicationService {

    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AccountApplicationService(AccountRepository accountRepository,
                                     ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Opens a new account for a customer.
     *
     * @param ownerId      Keycloak sub of the authenticated user
     * @param ownerName    display name of the account owner
     * @param currencyCode ISO 4217 currency code (e.g. "NGN", "USD")
     * @return the newly created account
     */
    public Account openAccount(String ownerId, String ownerName, String currencyCode) {
        AccountId accountId = AccountId.generate();
        AccountCommand.CreateAccount command =
            AccountCommand.CreateAccount.now(accountId, ownerName, currencyCode);

        Result<AccountService.AccountCreationResult> result =
            AccountService.createAccount(command);

        if (result.isFailure()) {
            throw new IllegalArgumentException("Failed to create account: " + result.getErrorOrNull());
        }

        AccountService.AccountCreationResult creation = result.getOrThrow();
        accountRepository.save(creation.account());
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
}

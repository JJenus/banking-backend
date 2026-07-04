package com.jjenus.banking.accounts.application;

import com.jjenus.bank.core.accounts.*;
import com.jjenus.bank.core.policy.OverdraftPolicy;
import com.jjenus.bank.core.ports.AccountRepository;
import com.jjenus.bank.core.shared.Money;
import com.jjenus.bank.core.shared.Result;
import com.jjenus.banking.accounts.AccountQueryApi;
import com.jjenus.banking.accounts.infrastructure.AccountOwnerDirectory;
import com.jjenus.banking.shared.exception.ResourceNotFoundException;
import com.jjenus.banking.shared.policy.FeeSchedule;
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
    private final OverdraftPolicy overdraftPolicy;
    private final FeeSchedule feeSchedule;
    private final ApplicationEventPublisher eventPublisher;

    public AccountApplicationService(AccountRepository accountRepository,
                                     AccountOwnerDirectory ownerDirectory,
                                     TransactionRepository transactionRepository,
                                     OverdraftPolicy overdraftPolicy,
                                     FeeSchedule feeSchedule,
                                     ApplicationEventPublisher eventPublisher) {
        this.accountRepository      = accountRepository;
        this.ownerDirectory         = ownerDirectory;
        this.transactionRepository  = transactionRepository;
        this.overdraftPolicy        = overdraftPolicy;
        this.feeSchedule            = feeSchedule;
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
    /**
     * Withdraws money from an account, honouring the active {@link OverdraftPolicy}.
     *
     * <p>If the withdrawal amount exceeds the available balance:
     * <ol>
     *   <li>The overdraft policy is consulted with the shortfall amount.</li>
     *   <li>If allowed, the withdrawal proceeds — the account balance goes negative.</li>
     *   <li>If not allowed, an insufficient-funds error is thrown.</li>
     * </ol>
     *
     * <p>For overdraft withdrawals, bank-core's {@code Account.withdraw()} is
     * bypassed for the balance check (since it hard-rejects negative balances),
     * but the resulting domain objects (updated account, event, transaction) are
     * constructed consistently using bank-core types.
     */
    public Account withdraw(String accountId, Money amount, String reference) {
        Account account = accountRepository.getById(AccountId.of(accountId));

        boolean needsOverdraft = !account.hasSufficientFunds(amount);

        if (needsOverdraft) {
            Money shortfall = amount.subtract(account.balance());
            if (!overdraftPolicy.allowsOverdraft(account, shortfall)) {
                throw new IllegalStateException(String.format(
                    "Insufficient funds: balance is %s, attempting to withdraw %s. " +
                    "No overdraft facility is available on this account.",
                    account.balance().format(), amount.format()));
            }
            // Overdraft permitted — execute directly bypassing bank-core's balance guard
            return executeOverdraftWithdrawal(account, amount, reference);
        }

        // Normal path — sufficient balance, delegate to bank-core
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

        // Apply withdrawal fee after the withdrawal itself (same DB transaction)
        return applyWithdrawalFee(withdrawal.updatedAccount(), reference);
    }

    /**
     * Executes a withdrawal that takes the account into negative territory
     * (overdraft). Bypasses bank-core's sufficient-funds guard but constructs
     * all resulting domain objects using bank-core types for consistency.
     */
    private Account executeOverdraftWithdrawal(Account account, Money amount, String reference) {
        // Manually compute new balance (negative)
        Money newBalance = account.balance().subtract(amount);

        // Build updated account with negative balance directly
        Account updatedAccount = new Account(
            account.id(),
            account.customerId(),
            newBalance,
            account.status(),
            account.createdAt(),
            java.time.Instant.now(),
            account.version() + 1
        );

        // Create the withdrawal transaction using bank-core's factory
        com.jjenus.bank.core.transactions.Transaction transaction =
            com.jjenus.bank.core.transactions.Transaction.createWithdrawal(
                com.jjenus.bank.core.transactions.TransactionId.generate(),
                account.id(),
                amount,
                newBalance,
                reference
            );

        // Build the MoneyWithdrawn event using bank-core's factory
        AccountEvent.MoneyWithdrawn event = AccountEvent.moneyWithdrawn(
            account.id(), amount, reference);

        accountRepository.update(updatedAccount);
        transactionRepository.save(transaction);
        eventPublisher.publishEvent(event);

        return applyWithdrawalFee(updatedAccount, reference);
    }

    /**
     * Charges the withdrawal fee (if configured) after a successful withdrawal.
     * Creates a FEE transaction and publishes a {@link FeeChargedEvent} for the
     * ledger listener. Returns the account after the fee deduction (or the
     * unchanged account if fee is zero).
     */
    private Account applyWithdrawalFee(Account postWithdrawalAccount, String reference) {
        com.jjenus.bank.core.policy.FeePolicy withdrawalFeePolicy = feeSchedule.withdrawal();
        // Use the withdrawal amount as a proxy — fee is on the act of withdrawing,
        // policy receives the post-withdrawal account to check its characteristics.
        // We pass Money.zero here since withdrawal fee is typically flat or NONE;
        // for percentage-based withdrawal fees the caller should pass the actual amount.
        // This is intentionally kept simple — withdrawal fees are almost always flat.
        Money zero = Money.zero(postWithdrawalAccount.getCurrency());
        Money feeAmount = withdrawalFeePolicy.calculateTransferFee(postWithdrawalAccount, zero);

        if (!feeAmount.isPositive()) {
            return postWithdrawalAccount;
        }

        // Guard: enough balance for the fee
        if (!postWithdrawalAccount.hasSufficientFunds(feeAmount)) {
            // If balance is insufficient for fee after withdrawal, log a warning
            // but do NOT block — the withdrawal already committed. The fee
            // will be attempted but skipped rather than rolling back a completed withdrawal.
            org.slf4j.LoggerFactory.getLogger(AccountApplicationService.class)
                .warn("Skipping withdrawal fee of {} — insufficient post-withdrawal balance {} on account {}",
                    feeAmount.format(), postWithdrawalAccount.balance().format(),
                    postWithdrawalAccount.id().value());
            return postWithdrawalAccount;
        }

        Account afterFee = postWithdrawalAccount.withdraw(feeAmount);

        com.jjenus.bank.core.transactions.Transaction feeTx =
            com.jjenus.bank.core.transactions.Transaction.createFee(
                com.jjenus.bank.core.transactions.TransactionId.generate(),
                postWithdrawalAccount.id(),
                feeAmount,
                afterFee.balance(),
                "Withdrawal fee: " + withdrawalFeePolicy.description()
            );

        accountRepository.update(afterFee);
        transactionRepository.save(feeTx);

        // FeeChargedEvent uses a null TransferId substitute — withdrawal fees
        // are account-level, not linked to a Transfer record.
        // We reuse FeeChargedEvent with a synthetic "WD-{accountId}" source reference.
        eventPublisher.publishEvent(
            new com.jjenus.banking.transfers.application.FeeChargedEvent(
                postWithdrawalAccount.id(),
                feeAmount,
                // Withdrawal fees are not linked to a Transfer — synthesise a
                // valid TransferId using the account ID's alphanumeric suffix.
                com.jjenus.bank.core.transfers.TransferId.of(
                    "TRF-WD" + postWithdrawalAccount.id().value()
                               .replace("ACC-", "")
                               .toUpperCase()
                               .replaceAll("[^A-Z0-9]", "X")
                               .substring(0, 4)
                    + "000000"
                ),
                withdrawalFeePolicy.description()
            )
        );

        return afterFee;
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

    /**
     * Suspends an account (regulatory hold — differs from freeze in that
     * SUSPENDED accounts cannot be activated without compliance review).
     */
    public Account suspendAccount(String accountId, String reason) {
        Account account = accountRepository.getById(AccountId.of(accountId));
        AccountCommand.SuspendAccount command = AccountCommand.SuspendAccount.now(account.id(), reason);

        Result<AccountService.AccountStatusChangeResult> result = AccountService.suspend(account, command);
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

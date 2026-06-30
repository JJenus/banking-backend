package com.jjenus.banking.transfers.application;

import com.jjenus.bank.core.accounts.Account;
import com.jjenus.bank.core.accounts.AccountId;
import com.jjenus.bank.core.ports.AccountRepository;
import com.jjenus.bank.core.ports.TransferRepository;
import com.jjenus.bank.core.shared.Money;
import com.jjenus.bank.core.shared.Result;
import com.jjenus.bank.core.transfers.*;
import com.jjenus.banking.shared.exception.ResourceNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Currency;

/**
 * Application service for transfer operations.
 *
 * <p>Key responsibilities:
 * <ol>
 *   <li>Idempotency enforcement via Redis — duplicate requests with the same
 *       {@code Idempotency-Key} header return the cached result immediately</li>
 *   <li>Load from/to accounts via the {@link AccountRepository} port</li>
 *   <li>Delegate execution to bank-core's {@link TransferService}</li>
 *   <li>Persist updated accounts and the transfer record</li>
 *   <li>Publish typed {@link TransferEvent} domain events after commit</li>
 * </ol>
 */
@Service
@Transactional
public class TransferApplicationService {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RedisTemplate<String, String> redis;

    private static final String IDEMPOTENCY_PREFIX = "idempotency:transfer:";
    private static final Duration IDEMPOTENCY_TTL  = Duration.ofHours(24);

    public TransferApplicationService(AccountRepository accountRepository,
                                      TransferRepository transferRepository,
                                      ApplicationEventPublisher eventPublisher,
                                      RedisTemplate<String, String> redis) {
        this.accountRepository  = accountRepository;
        this.transferRepository = transferRepository;
        this.eventPublisher     = eventPublisher;
        this.redis              = redis;
    }

    /**
     * Executes a transfer between two accounts.
     *
     * @param fromAccountId  source account ID
     * @param toAccountId    destination account ID
     * @param amount         transfer amount (must be positive)
     * @param currencyCode   ISO 4217 currency code
     * @param description    human-readable transfer description
     * @param reference      caller-supplied reference (invoice number, etc.)
     * @param idempotencyKey unique key per request — duplicate calls return the
     *                       same result without re-executing the transfer
     */
    public Transfer executeTransfer(
        String fromAccountId,
        String toAccountId,
        String amount,
        String currencyCode,
        String description,
        String reference,
        String idempotencyKey
    ) {
        // 1. Idempotency check
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
        String cached   = redis.opsForValue().get(redisKey);
        if (cached != null) {
            // Return the already-completed transfer
            return transferRepository.getById(TransferId.of(cached));
        }

        // 2. Load accounts
        Account fromAccount = accountRepository.findById(AccountId.of(fromAccountId))
            .orElseThrow(() -> ResourceNotFoundException.of("Account", fromAccountId));
        Account toAccount = accountRepository.findById(AccountId.of(toAccountId))
            .orElseThrow(() -> ResourceNotFoundException.of("Account", toAccountId));

        // 3. Build command
        Currency currency = Currency.getInstance(currencyCode);
        TransferId transferId = TransferId.generate();
        TransferCommand.InitiateTransfer command = TransferCommand.InitiateTransfer.now(
            transferId, fromAccount.id(), toAccount.id(),
            Money.of(amount, currency), description, reference
        );

        // 4. Execute via bank-core domain service
        Result<TransferService.TransferExecutionResult> result =
            TransferService.executeTransfer(fromAccount, toAccount, command);

        if (result.isFailure()) {
            throw new IllegalStateException(result.getErrorOrNull());
        }

        TransferService.TransferExecutionResult execution = result.getOrThrow();

        // 5. Persist
        accountRepository.update(execution.updatedFromAccount());
        accountRepository.update(execution.updatedToAccount());
        transferRepository.save(execution.transfer());

        // 6. Cache idempotency key
        redis.opsForValue().set(redisKey, execution.transfer().id().value(), IDEMPOTENCY_TTL);

        // 7. Publish domain events (after transaction commits via Spring Modulith)
        execution.domainEvents().forEach(eventPublisher::publishEvent);

        return execution.transfer();
    }

    /**
     * Reverses a completed transfer.
     */
    public Transfer reverseTransfer(String transferId, String reason) {
        Transfer transfer = transferRepository.getById(TransferId.of(transferId));

        Account receiver = accountRepository.getById(transfer.toAccountId());
        Account sender   = accountRepository.getById(transfer.fromAccountId());

        Result<TransferService.ReversalResult> result =
            TransferService.reverseTransfer(transfer, receiver, sender, reason);

        if (result.isFailure()) {
            throw new IllegalStateException(result.getErrorOrNull());
        }

        TransferService.ReversalResult reversal = result.getOrThrow();

        accountRepository.update(reversal.updatedReceiverAccount());
        accountRepository.update(reversal.updatedSenderAccount());
        transferRepository.update(reversal.reversedTransfer());

        reversal.domainEvents().forEach(eventPublisher::publishEvent);

        return reversal.reversedTransfer();
    }

    @Transactional(readOnly = true)
    public Transfer getTransfer(String transferId) {
        return transferRepository.findById(TransferId.of(transferId))
            .orElseThrow(() -> ResourceNotFoundException.of("Transfer", transferId));
    }
}

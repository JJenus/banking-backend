package com.jjenus.banking.accounts.api;

import com.jjenus.bank.core.accounts.Account;
import com.jjenus.bank.core.shared.Money;
import com.jjenus.banking.accounts.application.AccountApplicationService;
import com.jjenus.banking.identity.IdentityQueryApi;
import com.jjenus.banking.shared.exception.ResourceNotFoundException;
import com.jjenus.banking.shared.web.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Currency;
import java.util.List;

/**
 * REST controller for the {@code accounts} module.
 *
 * <p>Base path: {@code /api/v1/accounts}
 *
 * <p>All endpoints require a valid Keycloak JWT. Role requirements:
 * <ul>
 *   <li>CUSTOMER — can manage their own accounts</li>
 *   <li>TELLER   — can manage any account</li>
 *   <li>ADMIN    — full access including freeze/close</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/accounts")
@Tag(name = "Accounts", description = "Account lifecycle and balance management")
@SecurityRequirement(name = "bearer-key")
public class AccountController {

    private final AccountApplicationService accountService;
    private final IdentityQueryApi identityQueryApi;

    public AccountController(AccountApplicationService accountService,
                             IdentityQueryApi identityQueryApi) {
        this.accountService = accountService;
        this.identityQueryApi = identityQueryApi;
    }

    // ── Open account ──────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Open a new bank account")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<AccountResponse> openAccount(@Valid @RequestBody OpenAccountRequest request) {
        String ownerId = CurrentUser.id();

        // The authoritative display name lives in the identity module's profile
        // (set at registration, editable via PATCH /v1/identity/me). Fall back
        // to the JWT's "name" claim only if no banking profile has been
        // registered yet, so account opening never hard-fails on a missing profile.
        String ownerName = identityQueryApi.getFullNameByUserId(ownerId)
            .orElseGet(CurrentUser::fullName);

        Account account = accountService.openAccount(ownerId, ownerName, request.currencyCode());
        AccountResponse response = AccountResponse.from(account);
        return ResponseEntity
            .created(URI.create("/api/v1/accounts/" + account.id().value()))
            .body(response);
    }

    // ── Get account ───────────────────────────────────────────────────────

    @GetMapping("/{accountId}")
    @Operation(summary = "Get account details and current balance")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public AccountResponse getAccount(@PathVariable String accountId) {
        Account account = accountService.getAccount(accountId);
        return AccountResponse.from(account);
    }

    // ── List my accounts ──────────────────────────────────────────────────

    @GetMapping("/my")
    @Operation(summary = "List all accounts belonging to the authenticated user")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<AccountResponse> getMyAccounts() {
        String ownerId = CurrentUser.id();
        return accountService.getAccountsForOwner(ownerId)
            .stream()
            .map(AccountResponse::from)
            .toList();
    }

    // ── Deposit ───────────────────────────────────────────────────────────

    @PostMapping("/{accountId}/deposit")
    @Operation(summary = "Deposit money into an account")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public AccountResponse deposit(@PathVariable String accountId,
                                   @Valid @RequestBody MoneyOperationRequest request) {
        Currency currency = Currency.getInstance(request.currencyCode());
        Money amount = Money.of(request.amount().toPlainString(), currency);
        Account updated = accountService.deposit(accountId, amount, request.reference());
        return AccountResponse.from(updated);
    }

    // ── Withdraw ──────────────────────────────────────────────────────────

    @PostMapping("/{accountId}/withdraw")
    @Operation(summary = "Withdraw money from an account")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public AccountResponse withdraw(@PathVariable String accountId,
                                    @Valid @RequestBody MoneyOperationRequest request) {
        Currency currency = Currency.getInstance(request.currencyCode());
        Money amount = Money.of(request.amount().toPlainString(), currency);
        Account updated = accountService.withdraw(accountId, amount, request.reference());
        return AccountResponse.from(updated);
    }

    // ── Admin operations ──────────────────────────────────────────────────

    @PostMapping("/{accountId}/freeze")
    @Operation(summary = "Freeze an account (Admin/Compliance only)")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")
    public AccountResponse freeze(@PathVariable String accountId,
                                  @Valid @RequestBody ReasonRequest request) {
        return AccountResponse.from(accountService.freezeAccount(accountId, request.reason()));
    }

    @PostMapping("/{accountId}/activate")
    @Operation(summary = "Activate a frozen/suspended/dormant account")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")
    public AccountResponse activate(@PathVariable String accountId) {
        return AccountResponse.from(accountService.activateAccount(accountId));
    }

    @DeleteMapping("/{accountId}")
    @Operation(summary = "Close an account (only possible with zero balance)")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(@PathVariable String accountId,
                      @Valid @RequestBody ReasonRequest request) {
        accountService.closeAccount(accountId, request.reason());
    }

    // ── Request / Response records ────────────────────────────────────────

    public record OpenAccountRequest(
        @NotBlank @Size(min = 3, max = 3, message = "Must be a 3-letter ISO 4217 currency code")
        String currencyCode
    ) {}

    public record MoneyOperationRequest(
        @NotNull @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        @NotBlank @Size(min = 3, max = 3) String currencyCode,

        @NotBlank @Size(max = 100, message = "Reference must not exceed 100 characters")
        String reference
    ) {}

    public record ReasonRequest(
        @NotBlank @Size(max = 500) String reason
    ) {}

    public record AccountResponse(
        String id,
        String ownerId,
        String balance,
        String currency,
        String status,
        String createdAt,
        String lastUpdatedAt,
        long version
    ) {
        static AccountResponse from(Account account) {
            return new AccountResponse(
                account.id().value(),
                account.customerId(),
                account.balance().amount().toPlainString(),
                account.balance().currency().getCurrencyCode(),
                account.status().name(),
                account.createdAt().toString(),
                account.lastUpdatedAt().toString(),
                account.version()
            );
        }
    }
}

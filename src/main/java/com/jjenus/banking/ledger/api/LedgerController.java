package com.jjenus.banking.ledger.api;

import com.jjenus.bank.core.ledger.LedgerEntry;
import com.jjenus.banking.ledger.application.LedgerApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * REST controller for the {@code ledger} module.
 *
 * <p>Base path: {@code /api/v1/ledger}
 *
 * <p>The ledger has no write endpoints. Every journal entry traces back to
 * a domain event from {@code accounts} or {@code transfers}; there is no
 * "manually post an entry" API, by design, to preserve the audit trail.
 */
@RestController
@RequestMapping("/v1/ledger")
@Tag(name = "Ledger", description = "Double-entry journal queries — read-only")
@SecurityRequirement(name = "bearer-key")
public class LedgerController {

    private final LedgerApplicationService ledgerService;

    public LedgerController(LedgerApplicationService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/accounts/{accountId}/balance")
    @Operation(summary = "Compute the ledger-derived balance for an account")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN', 'COMPLIANCE')")
    public BalanceResponse getBalance(@PathVariable String accountId,
                                      @RequestParam String currency) {
        BigDecimal balance = ledgerService.computeBalance(accountId, currency);
        return new BalanceResponse(accountId, balance.toPlainString(), currency, Instant.now().toString());
    }

    @GetMapping("/accounts/{accountId}/balance-as-of")
    @Operation(summary = "Compute the ledger-derived balance for an account at a point in time")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN', 'COMPLIANCE')")
    public BalanceResponse getBalanceAsOf(@PathVariable String accountId,
                                          @RequestParam String currency,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant asOf) {
        BigDecimal balance = ledgerService.computeBalanceAsOf(accountId, currency, asOf);
        return new BalanceResponse(accountId, balance.toPlainString(), currency, asOf.toString());
    }

    @GetMapping("/accounts/{accountId}/entries")
    @Operation(summary = "List all journal entries involving an account")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN', 'COMPLIANCE')")
    public List<EntryResponse> getEntries(@PathVariable String accountId) {
        return ledgerService.getRawEntriesForAccount(accountId)
            .stream().map(EntryResponse::from).toList();
    }

    // ── Request / Response records ────────────────────────────────────────

    public record BalanceResponse(
        String accountId,
        String balance,
        String currency,
        String asOf
    ) {}

    public record EntryResponse(
        String id,
        String debitAccountId,
        String creditAccountId,
        String amount,
        String currency,
        String description,
        String reference,
        String sourceId,
        String postedAt
    ) {
        static EntryResponse from(LedgerEntry entry) {
            return new EntryResponse(
                entry.id().value(),
                entry.debitAccountId().value(),
                entry.creditAccountId().value(),
                entry.amount().amount().toPlainString(),
                entry.amount().currency().getCurrencyCode(),
                entry.description(),
                entry.reference(),
                entry.sourceId(),
                entry.postedAt().toString()
            );
        }
    }
}

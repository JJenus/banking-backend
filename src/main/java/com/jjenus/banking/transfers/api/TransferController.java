package com.jjenus.banking.transfers.api;

import com.jjenus.bank.core.transfers.Transfer;
import com.jjenus.banking.shared.web.CurrentUser;
import com.jjenus.banking.transfers.application.TransferApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

/**
 * REST controller for the {@code transfers} module.
 *
 * <p>Base path: {@code /api/v1/transfers}
 *
 * <p>All mutating endpoints require an {@code Idempotency-Key} header.
 * Duplicate requests with the same key within 24 hours return the original
 * response without re-executing the transfer.
 */
@RestController
@RequestMapping("/v1/transfers")
@Tag(name = "Transfers", description = "Money transfers between accounts")
@SecurityRequirement(name = "bearer-key")
public class TransferController {

    private final TransferApplicationService transferService;

    public TransferController(TransferApplicationService transferService) {
        this.transferService = transferService;
    }

    // ── Initiate transfer ─────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Initiate a transfer between two accounts")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public ResponseEntity<TransferResponse> initiateTransfer(
        @Valid @RequestBody InitiateTransferRequest request,
        @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey
    ) {
        Transfer transfer = transferService.executeTransfer(
            request.fromAccountId(),
            request.toAccountId(),
            request.amount().toPlainString(),
            request.currencyCode(),
            request.description(),
            request.reference(),
            idempotencyKey
        );
        return ResponseEntity
            .created(URI.create("/api/v1/transfers/" + transfer.id().value()))
            .body(TransferResponse.from(transfer));
    }

    // ── Get transfer ──────────────────────────────────────────────────────

    @GetMapping("/{transferId}")
    @Operation(summary = "Get transfer details and current status")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN', 'COMPLIANCE')")
    public TransferResponse getTransfer(@PathVariable String transferId) {
        return TransferResponse.from(transferService.getTransfer(transferId));
    }

    // ── Get transfers for an account ──────────────────────────────────────

    @GetMapping("/by-account/{accountId}")
    @Operation(summary = "List all transfers for an account (newest first)")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN', 'COMPLIANCE')")
    public List<TransferResponse> getTransfersForAccount(@PathVariable String accountId) {
        return transferService.getTransfersForAccount(accountId)
            .stream().map(TransferResponse::from).toList();
    }

    // ── List my transfers ─────────────────────────────────────────────────

    @GetMapping("/my")
    @Operation(summary = "List all transfers across all accounts owned by the authenticated user")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<TransferResponse> getMyTransfers() {
        return transferService.getTransfersForOwner(CurrentUser.id())
            .stream().map(TransferResponse::from).toList();
    }

    // ── Reverse transfer ──────────────────────────────────────────────────

    @PostMapping("/{transferId}/reverse")
    @Operation(summary = "Reverse a completed transfer (Admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    public TransferResponse reverseTransfer(
        @PathVariable String transferId,
        @Valid @RequestBody ReasonRequest request
    ) {
        return TransferResponse.from(transferService.reverseTransfer(transferId, request.reason()));
    }

    // ── Cancel transfer ───────────────────────────────────────────────────

    @PostMapping("/{transferId}/cancel")
    @Operation(summary = "Cancel a pending transfer")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'TELLER', 'ADMIN')")
    public TransferResponse cancelTransfer(
        @PathVariable String transferId,
        @Valid @RequestBody ReasonRequest request
    ) {
        return TransferResponse.from(transferService.cancelTransfer(transferId, request.reason()));
    }

    // ── Request / Response records ────────────────────────────────────────

    public record InitiateTransferRequest(
        @NotBlank String fromAccountId,
        @NotBlank String toAccountId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currencyCode,
        @Size(max = 500) String description,
        @NotBlank @Size(max = 100) String reference
    ) {}

    public record ReasonRequest(
        @NotBlank @Size(max = 500) String reason
    ) {}

    public record TransferResponse(
        String id,
        String fromAccountId,
        String toAccountId,
        String amount,
        String currency,
        String description,
        String reference,
        String status,
        String debitTransactionId,
        String creditTransactionId,
        String failureReason,
        String createdAt,
        String completedAt
    ) {
        static TransferResponse from(Transfer t) {
            return new TransferResponse(
                t.id().value(),
                t.fromAccountId().value(),
                t.toAccountId().value(),
                t.amount().amount().toPlainString(),
                t.amount().currency().getCurrencyCode(),
                t.description(),
                t.reference(),
                t.status().name(),
                t.debitTransactionId() != null ? t.debitTransactionId().value() : null,
                t.creditTransactionId() != null ? t.creditTransactionId().value() : null,
                t.failureReason(),
                t.createdAt().toString(),
                t.completedAt() != null ? t.completedAt().toString() : null
            );
        }
    }
}

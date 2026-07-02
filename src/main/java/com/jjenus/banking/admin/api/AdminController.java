package com.jjenus.banking.admin.api;

import com.jjenus.bank.core.accounts.Account;
import com.jjenus.bank.core.ports.AccountRepository;
import com.jjenus.bank.core.ports.TransferRepository;
import com.jjenus.bank.core.transfers.Transfer;
import com.jjenus.bank.core.transfers.TransferStatus;
import com.jjenus.banking.accounts.application.AccountApplicationService;
import com.jjenus.banking.audit.infrastructure.AuditLogEntry;
import com.jjenus.banking.audit.infrastructure.AuditLogRepository;
import com.jjenus.banking.identity.application.IdentityApplicationService;
import com.jjenus.banking.identity.infrastructure.UserProfileJpaEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST controller for the {@code admin} module.
 *
 * <p>Base path: {@code /api/v1/admin}
 *
 * <p>All endpoints require {@code ADMIN} role unless noted. The
 * {@code COMPLIANCE} role gets read access to accounts and audit log but
 * not to account status mutations.
 *
 * <p>This controller is intentionally thin — it delegates to the
 * application services of the modules it coordinates, going through
 * their public APIs rather than touching repositories directly.
 */
@RestController
@RequestMapping("/v1/admin")
@Tag(name = "Admin", description = "Operational dashboard — ADMIN and COMPLIANCE access")
@SecurityRequirement(name = "bearer-key")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AccountApplicationService accountService;
    private final IdentityApplicationService identityService;
    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final AuditLogRepository auditLogRepository;

    public AdminController(AccountApplicationService accountService,
                           IdentityApplicationService identityService,
                           TransferRepository transferRepository,
                           AccountRepository accountRepository,
                           AuditLogRepository auditLogRepository) {
        this.accountService     = accountService;
        this.identityService    = identityService;
        this.transferRepository = transferRepository;
        this.accountRepository  = accountRepository;
        this.auditLogRepository = auditLogRepository;
    }

    // ── System stats ──────────────────────────────────────────────────────

    @GetMapping("/stats")
    @Operation(summary = "System-wide counts: accounts, transfers, users")
    public Map<String, Long> getStats() {
        return Map.of(
            "totalAccounts",   accountRepository.count(),
            "totalTransfers",  transferRepository.count(),
            "pendingKycCount", (long) identityService.getPendingKycReviews().size(),
            "underReviewKyc",  (long) identityService.getUnderReviewKyc().size()
        );
    }

    // ── Account management ────────────────────────────────────────────────

    @GetMapping("/accounts/{accountId}")
    @Operation(summary = "Look up any account by ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER', 'COMPLIANCE')")
    public AccountView getAccount(@PathVariable String accountId) {
        return AccountView.from(accountService.getAccount(accountId));
    }

    @GetMapping("/accounts/by-owner/{ownerId}")
    @Operation(summary = "List all accounts for a Keycloak owner ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'TELLER', 'COMPLIANCE')")
    public List<AccountView> getAccountsByOwner(@PathVariable String ownerId) {
        return accountService.getAccountsForOwner(ownerId)
            .stream().map(AccountView::from).toList();
    }

    @PostMapping("/accounts/{accountId}/freeze")
    @Operation(summary = "Freeze an account")
    public AccountView freeze(@PathVariable String accountId,
                              @Valid @RequestBody ReasonRequest req) {
        return AccountView.from(accountService.freezeAccount(accountId, req.reason()));
    }

    @PostMapping("/accounts/{accountId}/activate")
    @Operation(summary = "Activate a frozen, suspended, or dormant account")
    public AccountView activate(@PathVariable String accountId) {
        return AccountView.from(accountService.activateAccount(accountId));
    }

    @PostMapping("/accounts/{accountId}/suspend")
    @Operation(summary = "Suspend an account (regulatory hold)")
    public AccountView suspend(@PathVariable String accountId,
                               @Valid @RequestBody ReasonRequest req) {
        return AccountView.from(accountService.suspendAccount(accountId, req.reason()));
    }

    @DeleteMapping("/accounts/{accountId}")
    @Operation(summary = "Close an account (zero balance required)")
    public ResponseEntity<Void> close(@PathVariable String accountId,
                                      @Valid @RequestBody ReasonRequest req) {
        accountService.closeAccount(accountId, req.reason());
        return ResponseEntity.noContent().build();
    }

    // ── Transfer management ───────────────────────────────────────────────

    @GetMapping("/transfers/pending")
    @Operation(summary = "List all pending transfers")
    public List<TransferView> getPendingTransfers() {
        return transferRepository.findByStatus(TransferStatus.PENDING)
            .stream().map(TransferView::from).toList();
    }

    @GetMapping("/transfers/failed")
    @Operation(summary = "List all failed transfers")
    public List<TransferView> getFailedTransfers() {
        return transferRepository.findByStatus(TransferStatus.FAILED)
            .stream().map(TransferView::from).toList();
    }

    // ── KYC management ────────────────────────────────────────────────────

    @GetMapping("/kyc/pending")
    @Operation(summary = "List all users with KYC submitted awaiting review")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")
    public List<UserProfileView> getPendingKyc() {
        return identityService.getPendingKycReviews()
            .stream().map(UserProfileView::from).toList();
    }

    @GetMapping("/kyc/under-review")
    @Operation(summary = "List all users with KYC under review")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")
    public List<UserProfileView> getUnderReviewKyc() {
        return identityService.getUnderReviewKyc()
            .stream().map(UserProfileView::from).toList();
    }

    @PostMapping("/kyc/{userId}/start-review")
    @Operation(summary = "Begin KYC review for a user")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")
    public UserProfileView startKycReview(@PathVariable String userId) {
        return UserProfileView.from(identityService.startKycReview(userId));
    }

    @PostMapping("/kyc/{userId}/approve")
    @Operation(summary = "Approve a user's KYC")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")
    public UserProfileView approveKyc(@PathVariable String userId) {
        return UserProfileView.from(identityService.approveKyc(userId));
    }

    @PostMapping("/kyc/{userId}/reject")
    @Operation(summary = "Reject a user's KYC with a reason")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")
    public UserProfileView rejectKyc(@PathVariable String userId,
                                     @Valid @RequestBody ReasonRequest req) {
        return UserProfileView.from(identityService.rejectKyc(userId, req.reason()));
    }

    // ── Audit log ─────────────────────────────────────────────────────────

    @GetMapping("/audit/{aggregateId}")
    @Operation(summary = "Get audit log entries for an aggregate (account or transfer ID)")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")
    public List<AuditView> getAuditLog(@PathVariable String aggregateId) {
        return auditLogRepository.findByAggregateIdOrderByOccurredAtAsc(aggregateId)
            .stream().map(AuditView::from).toList();
    }

    @GetMapping("/audit/actor/{actorId}")
    @Operation(summary = "Get audit log entries by Keycloak actor (user) ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")
    public List<AuditView> getAuditByActor(@PathVariable String actorId) {
        return auditLogRepository.findByActorOrderByOccurredAtDesc(actorId)
            .stream().map(AuditView::from).toList();
    }

    // ── Request / Response records ────────────────────────────────────────

    public record ReasonRequest(
        @NotBlank @Size(max = 500) String reason
    ) {}

    public record AccountView(
        String id, String ownerId, String balance,
        String currency, String status, long version
    ) {
        static AccountView from(Account a) {
            return new AccountView(
                a.id().value(), a.customerId(),
                a.balance().amount().toPlainString(),
                a.balance().currency().getCurrencyCode(),
                a.status().name(), a.version()
            );
        }
    }

    public record TransferView(
        String id, String fromAccountId, String toAccountId,
        String amount, String currency, String status,
        String reference, String createdAt
    ) {
        static TransferView from(Transfer t) {
            return new TransferView(
                t.id().value(), t.fromAccountId().value(), t.toAccountId().value(),
                t.amount().amount().toPlainString(),
                t.amount().currency().getCurrencyCode(),
                t.status().name(), t.reference(), t.createdAt().toString()
            );
        }
    }

    public record UserProfileView(
        String id, String email, String fullName,
        String kycStatus, String createdAt
    ) {
        static UserProfileView from(UserProfileJpaEntity p) {
            return new UserProfileView(
                p.getId(), p.getEmail(), p.getFullName(),
                p.getKycStatus(), p.getCreatedAt().toString()
            );
        }
    }

    public record AuditView(
        String id, String eventType, String aggregateId,
        String actor, String occurredAt
    ) {
        static AuditView from(AuditLogEntry e) {
            return new AuditView(
                e.getId(), e.getEventType(), e.getAggregateId(),
                e.getActor(), e.getOccurredAt().toString()
            );
        }
    }
}

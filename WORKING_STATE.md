# WORKING_STATE.md
# Read THIS file first each session. Do not scan the whole codebase.
# Updated: after final push (admin + tests complete)

## Repositories
- bank-core:        https://github.com/JJenus/bank-core       (domain library, DO NOT edit here)
- banking-backend:  https://github.com/JJenus/banking-backend  (this repo)

## Stack
Java 17 · Spring Boot 3.4.1 · Spring Modulith 1.3.1 · PostgreSQL 16
Redis 7 · Keycloak 26 self-hosted · Postal SMTP self-hosted
Flyway 10 · Thymeleaf · OpenPDF (com.lowagie.text) · springdoc-openapi

## Package root: com.jjenus.banking

## Module status — ALL COMPLETE
| Module        | Root package                     | Public API               | Controller base path      |
|---------------|----------------------------------|--------------------------|---------------------------|
| identity      | c.j.b.identity                   | IdentityQueryApi         | /v1/identity              |
| accounts      | c.j.b.accounts                   | AccountQueryApi          | /v1/accounts              |
| transfers     | c.j.b.transfers                  | (events only)            | /v1/transfers             |
| transactions  | c.j.b.transactions               | TransactionRepository    | (no controller)           |
| ledger        | c.j.b.ledger                     | LedgerQueryApi           | /v1/ledger                |
| notifications | c.j.b.notifications              | (event-driven)           | —                         |
| audit         | c.j.b.audit                      | (event-driven)           | —                         |
| reporting     | c.j.b.reporting                  | ReportingApplicationSvc  | /v1/reporting             |
| admin         | c.j.b.admin                      | (REST only)              | /v1/admin                 |
| sse           | c.j.b.sse                        | (event-driven + stream)  | /v1/events                |
| shared        | c.j.b.shared                     | —                        | —                         |

## SSE module (d5a613f)
- SseEmitterRegistry — ConcurrentHashMap<ownerId, List<SseEmitter>>, multi-tab support
- SseHeartbeatScheduler — @Scheduled every 30s, comment to prevent proxy timeouts
- SseEventDispatcher — @ApplicationModuleListener for all domain events, resolves ownerId via AccountQueryApi
- SseController — GET /v1/events/stream (5min timeout, CONNECTED on open), GET /v1/events/stats (ADMIN)
- AsyncConfig — ThreadPoolTaskExecutor (core=4, max=16, queue=100)
- @EnableScheduling added to BankingApplication

SSE event types: TRANSACTION_CREATED, BALANCE_UPDATED, TRANSFER_COMPLETED,
TRANSFER_REVERSED, ACCOUNT_STATUS_CHANGED, KYC_STATUS_CHANGED, FEE_CHARGED

Frontend: use @microsoft/fetch-event-source (supports Authorization header)

## Fee matrix (c8886e5)
- FeeScheduleProperties — @ConfigurationProperties, three nested slots
- FeeSchedule — record(intrabankTransfer, outgoingTransfer, withdrawal)
  forTransfer(boolean intrabank) selects policy at runtime
  summary() → FeeSummary{intrabankTransfer, outgoingTransfer, withdrawal}
- FeePolicyConfig — builds FeeSchedule from config, logs each slot at startup
- TransferApplicationService — detects intrabank via accountRepository.existsById()
- AccountApplicationService — applyWithdrawalFee() after every withdrawal
- GET /v1/transfers/fee-info → FeeSchedule.FeeSummary (all three descriptions)

Fee env vars: FEE_INTRABANK, FEE_OUTGOING, FEE_WITHDRAWAL (all default NONE)
Fee policy types per slot: NONE | PERCENTAGE | FLAT | NIGERIAN_INTERBANK

## TODO (future sprints)
- Integration tests with Testcontainers
- EventStore port adapter
- Paystack webhook handler for top-up
- KYC document upload
- HTTPS cert automation

## All committed features
1. Scaffold — pom, configs, SecurityConfig, BankingProperties, GlobalExceptionHandler,
   CurrentUser, docker-compose, Dockerfile, Flyway V001-V004, README/CONTRIBUTING/AGENT.md
2. Identity — UserProfileJpaEntity, KycStatus, IdentityEvent, IdentityApplicationService
   (implements IdentityQueryApi), IdentityController, KYC state machine with events
3. Ledger — SystemAccounts (CASH/FEE_INCOME/INTEREST_EXPENSE), LedgerEntryJpaEntity,
   LedgerEntryJpaRepository, LedgerRepositoryAdapter, LedgerListener (@AppModuleListener),
   LedgerApplicationService (implements LedgerQueryApi), LedgerController, Flyway V005
4. Accounts fixes — AccountQueryApi, AccountOwnerDirectory, ownerId/ownerName separation,
   AccountJpaRepository with updateOwnerName, AccountApplicationService implements AccountQueryApi,
   AccountController uses IdentityQueryApi for owner name, suspendAccount() added
5. Notifications — EmailService fully wired (no stubs): accountId→AccountQueryApi→ownerId
   →IdentityQueryApi→email. 13 Thymeleaf templates. KYC email notifications.
6. Reporting — AccountStatement, TrialBalance, StatementPdfGenerator (OpenPDF),
   ReportingApplicationService, ReportingController (JSON + PDF endpoints, trial balance)
7. Transfers+Transactions — TransferJpaEntity, TransferJpaRepository, TransferRepositoryAdapter,
   TransactionJpaEntity, TransactionJpaRepository, TransactionRepository, TransferController,
   TransferApplicationService fully wired (idempotency, save transactions), Flyway V006
8. Admin — AdminController: system stats, account lookup/freeze/activate/suspend/close,
   pending/failed transfers, KYC queue management, audit log queries
9. Tests — AccountApplicationServiceTest, IdentityApplicationServiceTest,
   LedgerListenerTest, TransferApplicationServiceTest, ReportingApplicationServiceTest

## Flyway migration history (V001-V006 all pushed)
V001 — banking schema + accounts table
V002 — transfers + transactions tables
V003 — ledger_entries table
V004 — audit_log + user_profiles tables
V005 — add currency column to ledger_entries
V006 — add currency column to transactions

## Key bank-core signatures (avoid re-reading files for these)

### Account record constructor (field order)
Account(AccountId id, String customerId, Money balance, AccountStatus status,
        Instant createdAt, Instant lastUpdatedAt, long version)
// customerId = Keycloak sub/ownerId (NOT display name)

### AccountCommand factories
CreateAccount.now(AccountId, String ownerName, String currencyCode)
DepositMoney.now(AccountId, Money, String reference)
WithdrawMoney.now(AccountId, Money, String reference)
FreezeAccount.now(AccountId, String reason)
ActivateAccount.now(AccountId)
CloseAccount.now(AccountId, String reason)
SuspendAccount.now(AccountId, String reason)
MarkAccountDormant.now(AccountId)

### Transfer record constructor (field order)
Transfer(TransferId id, AccountId fromAccountId, AccountId toAccountId,
         Money amount, TransferStatus status, String description, String reference,
         Instant createdAt, Instant completedAt,
         TransactionId debitTransactionId, TransactionId creditTransactionId,
         String failureReason)

### Transaction.create* factories (all return Transaction)
createDeposit(TransactionId, AccountId, Money amount, Money balanceAfter, String reference)
createWithdrawal(TransactionId, AccountId, Money amount, Money balanceAfter, String reference)
createTransferOut(TransactionId, AccountId, Money amount, Money balanceAfter, String reference, String linkedTxId)
createTransferIn(TransactionId, AccountId, Money amount, Money balanceAfter, String reference, String linkedTxId)
createFee(TransactionId, AccountId, Money amount, Money balanceAfter, String reference, String linkedTxId)
createRefund(TransactionId, AccountId, Money amount, Money balanceAfter, String reference, String linkedTxId)
createReversal(TransactionId, AccountId, Money amount, Money balanceAfter, String reference, String linkedTxId)

### LedgerEntry.for* factories (all 6 args unless noted)
forDeposit(LedgerEntryId, cashAccountId, customerAccountId, Money, reference, sourceId)
forWithdrawal(LedgerEntryId, customerAccountId, cashAccountId, Money, reference, sourceId)
forTransfer(LedgerEntryId, fromAccountId, toAccountId, Money, reference, sourceId)
forFee(LedgerEntryId, customerAccountId, feeAccountId, Money, description, sourceId)
forReversal(LedgerEntryId, LedgerEntry originalEntry, String reason)  // 3 args

### ID formats (bank-core regex)
AccountId:     ACC-[A-Z0-9]{10}
TransferId:    TRF-[A-Z0-9]{12}
TransactionId: TXN-[A-Z0-9]{12}
LedgerEntryId: JNL-[A-Z0-9]{12}

### System account IDs (not in banking.accounts table — ledger counter-parties only)
SystemAccounts.CASH             = ACC-SYSCASH001
SystemAccounts.FEE_INCOME       = ACC-SYSFEEINC1
SystemAccounts.INTEREST_EXPENSE = ACC-SYSINTEXP1

### AccountStatus
canDeposit()  → ACTIVE, DORMANT
canTransact() → ACTIVE only

### Cross-module query chain (notifications recipient resolution)
accountId → AccountQueryApi.getOwnerId() → Keycloak ownerId
         → IdentityQueryApi.getEmailByUserId() → email

### Event flow
Request → Controller → ApplicationService → bank-core → persist → publishEvent
[transaction commits]
→ @ApplicationModuleListener: LedgerListener, NotificationListener, AuditListener

## REST API surface (all under /api prefix from server.servlet.context-path)
POST   /v1/identity/register                    (public)
GET    /v1/identity/me                          (authenticated)
PATCH  /v1/identity/me
POST   /v1/identity/me/kyc/submit               (CUSTOMER)
GET    /v1/identity/kyc/pending                 (ADMIN, COMPLIANCE)
POST   /v1/identity/{id}/kyc/start-review
POST   /v1/identity/{id}/kyc/approve
POST   /v1/identity/{id}/kyc/reject
GET    /v1/identity/{id}                        (ADMIN, TELLER, COMPLIANCE)

POST   /v1/accounts                             (CUSTOMER, TELLER, ADMIN)
GET    /v1/accounts/{id}
GET    /v1/accounts/my                          (CUSTOMER)
POST   /v1/accounts/{id}/deposit                (TELLER, ADMIN)
POST   /v1/accounts/{id}/withdraw               (TELLER, ADMIN)
POST   /v1/accounts/{id}/freeze                 (ADMIN, COMPLIANCE)
POST   /v1/accounts/{id}/activate               (ADMIN, COMPLIANCE)
DELETE /v1/accounts/{id}                        (ADMIN)

POST   /v1/transfers                            (Idempotency-Key header required)
GET    /v1/transfers/{id}
GET    /v1/transfers/by-account/{accountId}
GET    /v1/transfers/my                         (CUSTOMER)
POST   /v1/transfers/{id}/reverse               (ADMIN)
POST   /v1/transfers/{id}/cancel

GET    /v1/ledger/accounts/{id}/balance         (?currency=NGN)
GET    /v1/ledger/accounts/{id}/balance-as-of   (?currency=NGN&asOf=ISO-instant)
GET    /v1/ledger/accounts/{id}/entries

GET    /v1/reporting/accounts/{id}/statement    (?from=ISO&to=ISO)
GET    /v1/reporting/accounts/{id}/statement/pdf
GET    /v1/reporting/trial-balance              (?currency=NGN&asOf=ISO — ADMIN, COMPLIANCE)

GET    /v1/admin/stats
GET    /v1/admin/accounts/{id}                  (ADMIN, TELLER, COMPLIANCE)
GET    /v1/admin/accounts/by-owner/{ownerId}
POST   /v1/admin/accounts/{id}/freeze
POST   /v1/admin/accounts/{id}/activate
POST   /v1/admin/accounts/{id}/suspend
DELETE /v1/admin/accounts/{id}
GET    /v1/admin/transfers/pending
GET    /v1/admin/transfers/failed
GET    /v1/admin/kyc/pending                    (ADMIN, COMPLIANCE)
GET    /v1/admin/kyc/under-review
POST   /v1/admin/kyc/{id}/start-review
POST   /v1/admin/kyc/{id}/approve
POST   /v1/admin/kyc/{id}/reject
GET    /v1/admin/audit/{aggregateId}            (ADMIN, COMPLIANCE)
GET    /v1/admin/audit/actor/{actorId}

## TODO (future sprints — not blocking launch)
- Integration tests with Testcontainers (Postgres + Redis)
- EventStore port adapter (optional — bank-core EventStore port)
- Paystack webhook handler for top-up flow
- KYC document upload (S3/local storage)
- HTTPS cert automation (Let's Encrypt via nginx)

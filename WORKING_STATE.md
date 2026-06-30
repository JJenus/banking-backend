# WORKING_STATE.md
# Single source of truth for the current implementation state.
# Updated on every commit. Read THIS file first, not the whole codebase.

## Repositories
- bank-core:        https://github.com/JJenus/bank-core       (domain library)
- banking-backend:  https://github.com/JJenus/banking-backend  (this repo)

## Stack
Java 17 · Spring Boot 3.4.1 · Spring Modulith 1.3.1 · PostgreSQL 16
Redis 7 · Keycloak 26 (self-hosted, Apache 2.0) · Postal SMTP (self-hosted, MIT)
Flyway 10 · Thymeleaf · OpenPDF · springdoc-openapi · Testcontainers

## Package root
com.jjenus.banking

## Module map
| Module        | Root package                        | Status      | Public API interface     |
|---------------|-------------------------------------|-------------|--------------------------|
| identity      | com.jjenus.banking.identity         | COMPLETE    | IdentityQueryApi         |
| accounts      | com.jjenus.banking.accounts         | COMPLETE    | AccountQueryApi          |
| transfers     | com.jjenus.banking.transfers        | PARTIAL*    | —                        |
| ledger        | com.jjenus.banking.ledger           | COMPLETE    | LedgerQueryApi           |
| notifications | com.jjenus.banking.notifications    | COMPLETE    | (event-driven, no API)   |
| audit         | com.jjenus.banking.audit            | COMPLETE    | (event-driven, no API)   |
| reporting     | com.jjenus.banking.reporting        | TODO        | ReportingQueryApi        |
| admin         | com.jjenus.banking.admin            | TODO        | (REST endpoints only)    |
| shared        | com.jjenus.banking.shared           | COMPLETE    | —                        |

*transfers: TransferApplicationService done, TransferJpaEntity/Adapter/Controller TODO

## What's done (by feature commit order)

### Scaffold commit (initial push)
- BankingApplication.java
- pom.xml (all 18 deps with exact versions)
- application.yml + application-local.yml
- SecurityConfig (Keycloak JWT, role extractor from realm_access.roles)
- BankingProperties (typed @ConfigurationProperties record)
- RedisConfig
- GlobalExceptionHandler (RFC 7807 ProblemDetail for all error types)
- ResourceNotFoundException
- CurrentUser (JWT claim extractor util)
- docker-compose.yml (8 services)
- Dockerfile (multi-stage, non-root, G1GC)
- docker/postgres/init.sql
- docker/keycloak/realm-banking.json (4 roles: CUSTOMER TELLER ADMIN COMPLIANCE)
- docker/nginx/nginx.conf
- docker/prometheus/prometheus.yml
- Flyway V001 (accounts table), V002 (transfers+transactions), V003 (ledger_entries), V004 (audit_log+user_profiles)
- README.md, CONTRIBUTING.md, AGENT.md, docs/architecture.md, docs/RUNNING.md
- ApplicationModulesTest

### Identity feature commit
- identity/domain/KycStatus.java (PENDING→SUBMITTED→UNDER_REVIEW→APPROVED/REJECTED)
- identity/domain/IdentityEvent.java (sealed: KycSubmitted, KycApproved, KycRejected)
- identity/infrastructure/UserProfileJpaEntity.java
- identity/infrastructure/UserProfileJpaRepository.java
- identity/application/IdentityApplicationService.java (implements IdentityQueryApi)
  - register(), getMyProfile(), updateProfile()
  - submitKyc(), startKycReview(), approveKyc(), rejectKyc()
  - Publishes IdentityEvent on every KYC transition
- identity/api/IdentityController.java
  - POST /v1/identity/register (public)
  - GET/PATCH /v1/identity/me
  - POST /v1/identity/me/kyc/submit (CUSTOMER)
  - GET /v1/identity/kyc/pending, /{id}/kyc/start-review, approve, reject (ADMIN/COMPLIANCE)
  - GET /v1/identity/{id} (ADMIN/TELLER/COMPLIANCE)
- IdentityQueryApi.java (getEmailByUserId, getFullNameByUserId, profileExists)

- FIXED resolveEmail() stub → EmailService now resolves via:
  accountId → AccountQueryApi.getOwnerId() → IdentityQueryApi.getEmailByUserId()
- FIXED accounts ownerId/ownerName conflation:
  Account.customerId() = Keycloak ownerId (not display name)
  Display name stored separately via AccountOwnerDirectory (owner_name column)
- NEW AccountQueryApi (getOwnerId, getOwnerName, accountExists)
  implemented by AccountApplicationService
- NEW AccountOwnerDirectory (JPA-layer metadata for display names)
- UPDATED AccountJpaRepository (findOwnerName, updateOwnerName queries)
- UPDATED AccountApplicationService (implements AccountQueryApi, injects AccountOwnerDirectory)
- UPDATED AccountController (injects IdentityQueryApi, resolves ownerName from profile)
- UPDATED NotificationListener (added KYC event handlers)
- UPDATED EmailService (real recipient resolution, sendKycSubmittedEmail etc.)

- 13 Thymeleaf email templates (all complete, no stubs):
  _base.html, account-opened, deposit-confirmation, withdrawal-confirmation,
  transfer-sent, transfer-received, transfer-reversed, transfer-failed-ops,
  account-restricted, account-activated, account-closed,
  kyc-submitted, kyc-approved, kyc-rejected

- package-info.java for accounts and identity modules

### Ledger feature (IN PROGRESS — not yet committed/pushed)
- ledger/domain/SystemAccounts.java (CASH=ACC-SYSCASH001, FEE_INCOME=ACC-SYSFEEINC1, INTEREST_EXPENSE=ACC-SYSINTEXP1)
- ledger/infrastructure/LedgerEntryJpaEntity.java
- ledger/infrastructure/LedgerEntryJpaRepository.java (computeBalance JPQL queries)
- ledger/infrastructure/LedgerRepositoryAdapter.java (implements bank-core LedgerRepository port)
- ledger/application/LedgerListener.java (@ApplicationModuleListener: onMoneyDeposited, onMoneyWithdrawn, onTransferCompleted, onTransferReversed)
- ledger/application/LedgerApplicationService.java (implements LedgerQueryApi)
- ledger/api/LedgerController.java (GET balance, balance-as-of, entries)
- LedgerQueryApi.java (computeBalance, computeBalanceAsOf, getEntriesForAccount, getEntriesForAccountInRange, LedgerEntryView record)
- ledger/package-info.java
- Flyway V005 (add currency column to ledger_entries)

## Key architectural decisions

### Bank-core port mapping
bank-core port interface         → Spring @Repository implementing it
AccountRepository                → AccountRepositoryAdapter
LedgerRepository                 → LedgerRepositoryAdapter
TransferRepository               → TransferRepositoryAdapter (TODO)
EventStore                       → EventStoreAdapter (TODO — not blocking)

### ownerId vs ownerName (IMPORTANT)
Account.customerId() = Keycloak sub UUID  (used for findByCustomerId)
AccountJpaEntity.ownerName = display name (set by AccountOwnerDirectory after save)
These are separate. Never conflate them.

### Cross-module query resolution chain (notifications)
accountId
  → AccountQueryApi.getOwnerId(accountId)     → Keycloak ownerId
  → IdentityQueryApi.getEmailByUserId(ownerId) → email address

### Event flow
Request → Controller → ApplicationService → bank-core → persist → publishEvent
[tx commits]
→ @ApplicationModuleListener: LedgerListener, NotificationListener, AuditListener

### SystemAccounts (ledger counter-parties)
NOT rows in banking.accounts table. No FK constraints on ledger_entries.
Valid bank-core AccountId format (ACC-XXXXXXXXXX, 10 uppercase alphanumeric).

## Flyway migration history
V001 — accounts table (banking schema)
V002 — transfers + transactions tables
V003 — ledger_entries table (no currency column — fixed in V005)
V004 — audit_log + user_profiles tables
V005 — add currency CHAR(3) to ledger_entries (IN PROGRESS, not yet pushed)

## bank-core key signatures (avoid re-reading files for these)

### Account record fields (order matters for constructor)
Account(AccountId id, String customerId, Money balance, AccountStatus status,
        Instant createdAt, Instant lastUpdatedAt, long version)

### AccountCommand factories
CreateAccount.now(AccountId, String ownerName, String currencyCode)
DepositMoney.now(AccountId, Money, String reference)
WithdrawMoney.now(AccountId, Money, String reference)
FreezeAccount.now(AccountId, String reason)
ActivateAccount.now(AccountId)
CloseAccount.now(AccountId, String reason)
SuspendAccount.now(AccountId, String reason)
MarkAccountDormant.now(AccountId)

### LedgerEntry static factories (all take: LedgerEntryId, AccountId, AccountId, Money, String ref, String sourceId)
LedgerEntry.forDeposit(id, cashAccount, customerAccount, amount, reference, sourceId)
LedgerEntry.forWithdrawal(id, customerAccount, cashAccount, amount, reference, sourceId)
LedgerEntry.forTransfer(id, fromAccount, toAccount, amount, reference, sourceId)
LedgerEntry.forFee(id, customerAccount, feeAccount, amount, description, sourceId)
LedgerEntry.forReversal(id, originalEntry, reason)
LedgerEntry.forInterest(id, interestExpenseAccount, customerAccount, amount, reference, sourceId)

### AccountId format
Pattern: ACC-[A-Z0-9]{10}  (exactly 10 uppercase alphanumeric after prefix)

### LedgerEntryId format
Pattern: JNL-[A-Z0-9]{12}  (exactly 12 uppercase alphanumeric after prefix)

### TransferId format
Pattern: TRF-[A-Z0-9]{12}

### TransactionId format
Pattern: TXN-[A-Z0-9]{12}

### AccountStatus.canDeposit() returns true for: ACTIVE, DORMANT
### AccountStatus.canTransact() returns true for: ACTIVE only

### TransferEvent records
TransferInitiated(eventId, occurredOn, transferId, fromAccountId, toAccountId, amount, reference)
TransferDebited(eventId, occurredOn, transferId, fromAccountId, amount, debitTransactionId)
TransferCredited(eventId, occurredOn, transferId, toAccountId, amount, creditTransactionId)
TransferCompleted(eventId, occurredOn, transferId, fromAccountId, toAccountId, amount)
TransferFailed(eventId, occurredOn, transferId, reason)
TransferReversed(eventId, occurredOn, transferId, originalFromAccountId, originalToAccountId, amount, reason, reversalDebitTransactionId, reversalCreditTransactionId)
TransferCancelled(eventId, occurredOn, transferId, reason)

### AccountEvent records
AccountCreated(eventId, occurredOn, accountId, ownerName, currency)
MoneyDeposited(eventId, occurredOn, accountId, amount, reference)
MoneyWithdrawn(eventId, occurredOn, accountId, amount, reference)
AccountFrozen(eventId, occurredOn, accountId, reason)
AccountActivated(eventId, occurredOn, accountId)
AccountClosed(eventId, occurredOn, accountId, reason)
AccountSuspended(eventId, occurredOn, accountId, reason)
AccountMarkedDormant(eventId, occurredOn, accountId)

## TODO — remaining features to implement
1. ✅ identity module
2. ✅ ledger module (listener + persistence + queries)  ← IN PROGRESS
3. transfers JPA + controller (TransferJpaEntity, TransferRepositoryAdapter, TransferController)
4. TransactionJpaEntity + TransactionRepositoryAdapter (for statement lines)
5. reporting module (account statements, trial balance, PDF export via OpenPDF)
6. admin module (ops REST endpoints: list all accounts, freeze/unfreeze any, KYC queue, system stats)
7. ApplicationModulesTest (verify all boundaries pass)
8. Integration tests (AccountApplicationServiceTest, TransferApplicationServiceTest, LedgerListenerTest)

# Architecture

## Overview

`banking-backend` is a **modular monolith** — a single deployable Spring Boot JAR whose internal structure is divided into seven explicitly bounded modules. Module boundaries are enforced at test time by Spring Modulith. Crossing a boundary is a CI failure.

This design gives the operational simplicity of a monolith (one process, one DB connection pool, one deploy) with the domain separation of microservices. If the system ever needs to scale horizontally, individual modules can be extracted into services without rewriting business logic — because the business logic is already cleanly separated.

---

## The key design decision: bank-core is the domain

All banking business rules live in the [`bank-core`](https://github.com/JJenus/bank-core) library:

- Account lifecycle (ACTIVE → FROZEN → SUSPENDED → DORMANT → CLOSED)
- Balance calculation (immutable records, no mutation)
- Transfer execution and reversal
- Double-entry ledger enforcement
- Fee policies, overdraft policies
- Domain events (`AccountEvent`, `TransferEvent`, `LedgerEvent`)
- Port interfaces (`AccountRepository`, `EventStore`, `TransferRepository`, `LedgerRepository`)

This project implements the ports and orchestrates the commands. It never contains business logic.

---

## Module architecture

### Module boundary rules

Every module has a root package and internal sub-packages:

```
com.jjenus.banking.accounts          ← PUBLIC (other modules may reference classes here)
com.jjenus.banking.accounts.api      ← INTERNAL (controllers — no cross-module access)
com.jjenus.banking.accounts.application  ← INTERNAL (services)
com.jjenus.banking.accounts.infrastructure ← INTERNAL (JPA)
```

Spring Modulith enforces this at test time. `ApplicationModulesTest` will fail if any module imports a class from another module's sub-package.

### Cross-module communication

The only permitted form is through the Spring `ApplicationEventPublisher`:

```
accounts module publishes: AccountEvent.MoneyDeposited
                                    ↓
ledger module listens:     LedgerListener.onMoneyDeposited()  → posts LedgerEntry
notifications module:      NotificationListener.onMoneyDeposited() → sends email
audit module:              AuditListener.onDomainEvent() → writes audit log row
```

All listeners use `@ApplicationModuleListener` (not `@EventListener`), which means:
1. Events fire **after** the originating transaction commits (no phantom reads)
2. Failed event processing is retried by the Spring Modulith event publication log
3. Notification failures cannot roll back a banking transaction

---

## Request lifecycle

```
[JWT Token from Keycloak]
        ↓
[Nginx reverse proxy]
        ↓
[Spring Security — JWT validation against Keycloak JWK URI]
        ↓
[Controller — validates input, extracts CurrentUser.id()]
        ↓
[ApplicationService — loads domain objects via Repository ports]
        ↓
[bank-core service/command — executes domain logic, returns result]
        ↓
[ApplicationService — persists result, publishes domain events]
        ↓ (transaction commits)
[Spring Modulith event publication]
        ↓
[@ApplicationModuleListener — ledger, notifications, audit]
        ↓
[Response returned to client]
```

---

## Authentication: Keycloak

Keycloak is self-hosted on the same VPS. It issues JWTs; this application only validates them.

**What Keycloak owns:** login, logout, session management, MFA, password reset, role assignment, brute-force protection.

**What this app owns:** business roles as `@PreAuthorize` rules, and the `UserProfile` entity which extends Keycloak's `sub` claim with banking-specific data (KYC status, phone number, etc.).

**JWT role extraction:**

Keycloak encodes roles in `realm_access.roles`. The `JwtAuthenticationConverter` in `SecurityConfig` maps these to Spring `ROLE_*` authorities. Controllers use `@PreAuthorize("hasRole('CUSTOMER')")` directly.

**Roles:**

| Role | Access |
|---|---|
| `CUSTOMER` | Own accounts and transfers |
| `TELLER` | All accounts and deposits/withdrawals |
| `ADMIN` | Full system access including freeze/close |
| `COMPLIANCE` | Read-only audit + can freeze accounts |

---

## Email: Postal SMTP

Postal is self-hosted on the same VPS. Spring Boot's `JavaMailSender` connects to it on `localhost:2525` (Docker internal: `postal:25`). No email leaves this server without going through Postal.

**Email flow:**
```
Domain event published
  → @ApplicationModuleListener in NotificationListener
  → EmailService.send*()  [runs @Async, won't block the request thread]
  → Thymeleaf renders HTML template
  → JavaMailSender → Postal SMTP → customer inbox
```

Postal provides a web UI at port 5000 for monitoring delivery status, bounce tracking, and suppression lists.

---

## Database schema

All application tables live in the `banking` schema. Keycloak uses a separate `keycloak_db` database. Postal uses `postal_db`. All three are on the same PostgreSQL instance but are fully isolated.

```
bank_db
└── banking schema
    ├── accounts          ← AccountJpaEntity
    ├── transactions      ← TransactionJpaEntity
    ├── transfers         ← TransferJpaEntity
    ├── ledger_entries    ← LedgerEntryJpaEntity (append-only)
    ├── audit_log         ← AuditLogEntry (append-only)
    └── user_profiles     ← UserProfileJpaEntity
```

Flyway owns all schema changes. Hibernate is set to `validate` — it verifies the schema matches the entities on startup but never alters it.

---

## Idempotency

Transfer requests must include an `Idempotency-Key` header. The flow:

1. Check Redis for the key (TTL: 24 hours)
2. If found → return the cached transfer ID immediately (no re-execution)
3. If not found → execute, persist result, cache the transfer ID in Redis

This prevents duplicate transfers from retried HTTP requests, network timeouts, or frontend double-submits.

---

## Observability

- **Spring Actuator** — `/api/actuator/health`, `/api/actuator/prometheus`
- **Micrometer** — auto-instruments all HTTP requests, JVM metrics, DB pool metrics
- **Prometheus** — scrapes `/actuator/prometheus` every 10 seconds
- **Grafana** — pre-provisioned dashboards for HTTP request rates, DB pool usage, JVM heap

All self-hosted on the same Docker Compose stack.

---

## CI/CD

GitHub Actions pipeline on push to `main`:

1. **Test** — runs `./mvnw verify` against Testcontainers (Postgres + Redis)
2. **Module boundary check** — runs `ApplicationModulesTest`
3. **Docker build** — builds multi-stage image, pushes to GitHub Container Registry
4. **Deploy** — SSHs into VPS, pulls new image, restarts app container only

The database, Keycloak, Postal, Prometheus, and Grafana are not restarted on deploy. Only the Spring Boot application container is replaced, giving near-zero downtime deploys for a stateless service.

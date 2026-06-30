# AGENT.md — Instructions for AI Coding Agents

> Read this file before touching any code in this repository.
> This applies to Claude Code, GitHub Copilot, Cursor, and any other AI agent.

---

## What this project is

A **modular monolith Spring Boot banking backend** built on the [`bank-core`](https://github.com/JJenus/bank-core) domain library. It is self-hosted — no external API calls, no SaaS dependencies. Everything runs in Docker on a VPS.

**Tech stack:** Java 17 · Spring Boot 3.4 · Spring Modulith 1.4 · PostgreSQL 16 · Redis 7 · Keycloak 26 · Postal SMTP · Flyway · OpenPDF

---

## The single most important rule

**Module boundaries are law.** `ApplicationModulesTest.moduleStructureIsValid()` runs on every PR. If it fails, the PR does not merge — period.

A module is a top-level package under `com.jjenus.banking`:
```
com.jjenus.banking.accounts       ← module root (public API)
com.jjenus.banking.accounts.api   ← internal (controllers)
com.jjenus.banking.accounts.application ← internal (services)
com.jjenus.banking.accounts.infrastructure ← internal (JPA)
```

**Allowed:** Module A imports a class from `com.jjenus.banking.moduleB` (the root package only).

**Forbidden:** Module A imports `com.jjenus.banking.moduleB.infrastructure.SomeJpaEntity`.

**Forbidden:** Module A `@Autowires` a Service bean from Module B.

**Correct cross-module communication:** Publish a domain event via `ApplicationEventPublisher`. The other module subscribes with `@ApplicationModuleListener`.

---

## Module map

| Module | Root package | Owns | Public API surface |
|---|---|---|---|
| `accounts` | `com.jjenus.banking.accounts` | Account CRUD, balance | `AccountApplicationService` |
| `transfers` | `com.jjenus.banking.transfers` | Transfer execution, reversal | `TransferApplicationService` |
| `ledger` | `com.jjenus.banking.ledger` | Double-entry journal | None (event-driven only) |
| `notifications` | `com.jjenus.banking.notifications` | Email dispatch | None (event-driven only) |
| `audit` | `com.jjenus.banking.audit` | Immutable audit log | None (event-driven only) |
| `reporting` | `com.jjenus.banking.reporting` | Statements, PDF export | `ReportingQueryService` |
| `identity` | `com.jjenus.banking.identity` | User profiles, KYC | `IdentityQueryApi` |
| `admin` | `com.jjenus.banking.admin` | Ops dashboard API | REST endpoints only |
| `shared` | `com.jjenus.banking.shared` | Config, exceptions, web utils | Everything is public |

---

## Domain logic lives in bank-core, not here

This project **never** contains banking domain logic. All of that is in the [`bank-core`](https://github.com/JJenus/bank-core) library:

- Do **not** add balance calculation logic here — it's in `Account.deposit()` / `Account.withdraw()`
- Do **not** add double-entry logic here — it's in `Ledger.post()`
- Do **not** add transfer validation here — it's in `TransferService.executeTransfer()`

The application layer here is purely orchestration: load → command → persist → publish event.

---

## Event flow

Every state change follows this pattern:

```
HTTP Request
  → Controller (validates input, extracts JWT)
  → ApplicationService (load, command, persist, publishEvent)
  → [transaction commits]
  → @ApplicationModuleListener (notifications, ledger, audit)
```

Events are published via `ApplicationEventPublisher`. Listeners use `@ApplicationModuleListener` (not `@EventListener`) so they fire **after the transaction commits**.

---

## Database rules

1. **Never modify a Flyway migration file.** Create a new `V00N__description.sql` instead.
2. **Never alter schema with JPA.** `spring.jpa.hibernate.ddl-auto=validate` — Hibernate only checks, never changes.
3. **Audit log is append-only.** No UPDATE or DELETE on `banking.audit_log`, ever.
4. **Ledger is append-only.** No UPDATE or DELETE on `banking.ledger_entries`, ever. Corrections are new reversal entries.

---

## Security rules

- **Never expose the Keycloak client secret** in code, logs, or test output.
- **Never log full request bodies** — they may contain account numbers or amounts.
- **Never log JWT tokens.**
- **Never skip `@PreAuthorize` on a controller method** — if you're unsure, add `@PreAuthorize("isAuthenticated()")` as a minimum.
- ADMIN and COMPLIANCE roles are only assignable in the Keycloak admin UI — never via a REST endpoint.

---

## When adding a new module

1. Create `com.jjenus.banking.{modulename}` package.
2. Sub-packages: `api/`, `application/`, `domain/` (if needed), `infrastructure/`.
3. Add a `package-info.java` to the root package with a Javadoc description.
4. Add the corresponding Flyway migration for any new tables.
5. Run `ApplicationModulesTest` locally to confirm boundaries are clean.
6. Document the module in `docs/modules/` following the existing template.

---

## When adding a new API endpoint

1. Controller goes in `{module}/api/`.
2. Always add `@PreAuthorize` with the correct role.
3. Request/response types are nested records inside the controller class.
4. All responses follow RFC 7807 Problem Details for errors (handled by `GlobalExceptionHandler`).
5. Always add `@Operation` and `@Tag` annotations for OpenAPI docs.

---

## Running locally

```bash
# Start dependencies only
docker compose up -d postgres redis keycloak

# Run the app with local profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

See `docs/RUNNING.md` for full local setup instructions.

---

## Do not

- Do not call any external HTTP API (no Firebase, no Twilio, no Stripe, no SendGrid)
- Do not add `@CrossOrigin` to controllers — CORS is configured globally in `SecurityConfig`
- Do not use `System.out.println` — use `LoggerFactory.getLogger(...)`
- Do not catch and swallow exceptions silently — log them or rethrow
- Do not write business logic in controllers or JPA repositories
- Do not add `spring.jpa.hibernate.ddl-auto=create` or `update` to any profile

# Contributing to Banking Backend

Thank you for contributing. This document covers everything you need to know before opening a PR.

---

## Prerequisites

| Tool | Version | Install |
|---|---|---|
| Java | 17 | [Adoptium](https://adoptium.net) |
| Maven | 3.9+ | `brew install maven` or [maven.apache.org](https://maven.apache.org) |
| Docker | 24+ | [docker.com](https://docker.com) |
| Docker Compose | V2 | Bundled with Docker Desktop |
| Git | 2.40+ | `brew install git` |

You also need the `bank-core` library installed in your local Maven repo. See setup below.

---

## First-time setup

```bash
# 1. Clone this repo
git clone https://github.com/JJenus/banking-backend.git
cd banking-backend

# 2. Install bank-core into local Maven repo
git clone https://github.com/JJenus/bank-core.git ../bank-core
mvn install -f ../bank-core/pom.xml -DskipTests

# 3. Copy environment file
cp .env.example .env
# Edit .env if needed (defaults work for local dev)

# 4. Start infrastructure dependencies
docker compose up -d postgres redis keycloak

# 5. Wait for Keycloak (~60 seconds on first boot), then run the app
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The API is now available at `http://localhost:8080/api`.
Swagger UI: `http://localhost:8080/api/swagger-ui.html`
Keycloak admin: `http://localhost:8180/admin` (admin / changeme)

---

## Branch strategy

| Branch | Purpose |
|---|---|
| `main` | Production-ready. Protected. All merges via PR. |
| `develop` | Integration branch. PRs go here first. |
| `feat/your-feature` | Feature branches. Branch from `develop`. |
| `fix/your-fix` | Bug fix branches. Branch from `main` for hotfixes, `develop` otherwise. |

```bash
# Start a new feature
git checkout develop
git pull origin develop
git checkout -b feat/ledger-pdf-export
```

---

## Commit message format

We use [Conventional Commits](https://www.conventionalcommits.org):

```
<type>(<scope>): <short description>

[optional body]

[optional footer]
```

**Types:** `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `perf`

**Scopes:** `accounts`, `transfers`, `ledger`, `notifications`, `audit`, `reporting`, `identity`, `admin`, `shared`, `docker`, `ci`

**Examples:**
```
feat(transfers): add idempotency key validation in TransferApplicationService
fix(accounts): correct DORMANT account deposit validation path
docs(ledger): add double-entry journal architecture note
chore(docker): update Keycloak image to 26.1
```

---

## Code standards

### Package structure — follow this exactly

```
com.jjenus.banking.{module}/
├── api/            ← REST controllers, request/response records
├── application/    ← Application services (orchestration only, no domain logic)
├── domain/         ← Domain objects specific to this module (if any)
└── infrastructure/ ← JPA entities, Spring Data repos, port adapters
```

### The layering rule

```
api → application → bank-core domain → infrastructure
```

- Controllers call Application Services only
- Application Services call bank-core (domain) and Repositories (ports)
- Infrastructure implements bank-core ports — it is never called directly by controllers

### No domain logic in this repo

All banking business rules live in [`bank-core`](https://github.com/JJenus/bank-core). If you find yourself writing balance calculation, fee calculation, or transfer validation code here, stop and add it to bank-core instead.

### Cross-module communication

```java
// ✅ Correct — publish an event
eventPublisher.publishEvent(new AccountFrozen(...));

// ❌ Wrong — direct service injection across module boundary
@Autowired
private LedgerApplicationService ledger;  // accounts module cannot inject ledger service
```

---

## Running tests

```bash
# All tests
./mvnw verify

# Unit tests only (fast)
./mvnw test

# Module boundary check (required before every PR)
./mvnw test -Dtest=ApplicationModulesTest

# Single module tests
./mvnw test -Dtest="com.jjenus.banking.accounts.*"
```

Integration tests use Testcontainers — Docker must be running.

---

## Database migrations

**Never modify an existing Flyway migration.** If you need to change a table, add a new migration file:

```sql
-- src/main/resources/db/migration/V005__add_account_alias_column.sql
ALTER TABLE banking.accounts ADD COLUMN alias VARCHAR(100);
```

Migration file naming: `V{number}__{description}.sql`
- Numbers are sequential: V001, V002, V003...
- Description uses underscores, not spaces
- Always include a comment explaining what the migration does

---

## Email templates

Email templates are Thymeleaf HTML files in `src/main/resources/templates/email/`. Each template receives standard variables:

| Variable | Value |
|---|---|
| `fromName` | Configured in `banking.notifications.from-name` |
| `supportEmail` | Configured in `banking.notifications.support-email` |

Additional variables are passed per-template from `EmailService`. To add a new email:

1. Add the template file in `templates/email/`
2. Add a method in `EmailService` following the existing pattern
3. Call it from the appropriate `@ApplicationModuleListener` method in `NotificationListener`

---

## Adding a new module

1. Create the package: `com.jjenus.banking.{modulename}`
2. Create sub-packages: `api/`, `application/`, `infrastructure/`
3. Add `package-info.java` with a Javadoc block describing the module's responsibility
4. Add Flyway migration(s) for any new tables
5. Document the module in `docs/modules/{modulename}.md`
6. Run `ApplicationModulesTest` to confirm no boundary violations

---

## Pull request checklist

Before opening a PR, confirm:

- [ ] `./mvnw verify` passes (all tests green)
- [ ] `ApplicationModulesTest` passes (module boundaries clean)
- [ ] New Flyway migration added for any schema changes
- [ ] No `System.out.println` — use `Logger`
- [ ] All new controller methods have `@PreAuthorize`
- [ ] All new controller methods have `@Operation` for OpenAPI docs
- [ ] No external HTTP calls added (no RestTemplate/WebClient calling external services)
- [ ] PR description explains *what* and *why*, not just *what*
- [ ] Branch is up to date with `develop`

---

## Getting help

- Open a GitHub Discussion for questions
- Open a GitHub Issue for bugs with reproduction steps
- Tag `@JJenus` on PRs that need architectural review

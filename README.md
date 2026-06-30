# Banking Backend

A self-hosted modular monolith banking backend built on [`bank-core`](https://github.com/JJenus/bank-core).

**No external API calls. No SaaS. Everything runs on your VPS.**

---

## Architecture

```
Nuxt 4 Frontend  →  Nginx  →  Spring Boot (banking-backend)
                         ↘  Keycloak (IAM / OAuth2)
```

Seven Spring Modulith modules, enforced boundaries, internal event bus:

| Module | Responsibility |
|---|---|
| `identity` | User profiles, KYC state machine |
| `accounts` | Account lifecycle, balance management |
| `transfers` | Transfer execution, reversal, idempotency |
| `ledger` | Double-entry journal (driven by events) |
| `notifications` | Email dispatch via Postal SMTP (driven by events) |
| `audit` | Immutable compliance event log (driven by events) |
| `reporting` | Account statements, trial balance, PDF export |

Domain logic (balance rules, transfer validation, double-entry) lives in the [`bank-core`](https://github.com/JJenus/bank-core) library. This project is pure orchestration.

---

## Tech stack

| Layer | Technology | License |
|---|---|---|
| Framework | Spring Boot 3.4 + Spring Modulith 1.4 | Apache 2.0 |
| Domain library | bank-core | MIT |
| Auth / IAM | Keycloak 26 (self-hosted) | Apache 2.0 |
| Database | PostgreSQL 16 | PostgreSQL |
| Migrations | Flyway 10 | Apache 2.0 |
| Cache | Redis 7 | BSD |
| Email server | Postal (self-hosted) | MIT |
| Email templates | Thymeleaf | Apache 2.0 |
| PDF | OpenPDF | LGPL 2.1 |
| API docs | springdoc-openapi | Apache 2.0 |
| Metrics | Micrometer + Prometheus + Grafana | Apache 2.0 / AGPL |
| Reverse proxy | Nginx | BSD |

Zero paid SaaS. Zero external API calls at runtime.

---

## Quick start (local development)

### Prerequisites
- Java 17
- Docker + Docker Compose V2
- Maven 3.9+

### 1. Install bank-core

```bash
git clone https://github.com/JJenus/bank-core.git ../bank-core
mvn install -f ../bank-core/pom.xml -DskipTests
```

### 2. Start infrastructure

```bash
cp .env.example .env
docker compose up -d postgres redis keycloak
```

Wait ~60 seconds for Keycloak to initialise on first boot.

### 3. Run the application

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### 4. Verify it's running

```bash
curl http://localhost:8080/api/actuator/health
# → {"status":"UP"}
```

**URLs:**
- API: `http://localhost:8080/api`
- Swagger UI: `http://localhost:8080/api/swagger-ui.html`
- Keycloak admin: `http://localhost:8180/admin` → admin / changeme
- Grafana: `http://localhost:3001` → admin / changeme

---

## Running all services

```bash
# Full stack including Postal SMTP and Grafana
docker compose up -d

# View logs
docker compose logs -f app
docker compose logs -f keycloak

# Stop everything
docker compose down

# Stop and delete all data volumes (destructive)
docker compose down -v
```

---

## Running tests

```bash
# All tests (requires Docker for Testcontainers)
./mvnw verify

# Unit tests only
./mvnw test

# Verify module boundaries
./mvnw test -Dtest=ApplicationModulesTest
```

---

## Keycloak setup

On first run, the `banking` realm is auto-imported from `docker/keycloak/realm-banking.json`.

**Roles:**
- `CUSTOMER` — standard bank customer
- `TELLER` — bank teller (deposit/withdrawal access)
- `ADMIN` — full system access
- `COMPLIANCE` — read-only audit access + can freeze accounts

**To create a test user:**
1. Open `http://localhost:8180/admin`
2. Select the `banking` realm
3. Go to Users → Add user
4. Set a password in the Credentials tab
5. Assign the `CUSTOMER` role in the Role Mappings tab

**To get a token for testing:**
```bash
curl -X POST http://localhost:8180/realms/banking/protocol/openid-connect/token \
  -d "client_id=banking-frontend" \
  -d "grant_type=password" \
  -d "username=testuser" \
  -d "password=testpass" \
  | jq .access_token
```

---

## Environment variables

See `.env.example` for all available environment variables with descriptions.

**Required in production:**
- `POSTGRES_PASSWORD` — strong database password
- `REDIS_PASSWORD` — strong Redis password
- `KEYCLOAK_ADMIN_PASSWORD` — Keycloak admin password
- `POSTAL_SECRET_KEY` — generate with `openssl rand -hex 64`

---

## API documentation

Full OpenAPI spec is available at runtime:
- Swagger UI: `/api/swagger-ui.html`
- Raw OpenAPI JSON: `/api/api-docs`

See `docs/api/` for static API documentation.

---

## Deployment

```bash
# On your VPS — first time only
git clone https://github.com/JJenus/banking-backend.git /opt/banking-backend
cd /opt/banking-backend
cp .env.example .env
# Edit .env with production values
docker compose up -d
```

Subsequent deployments are automatic via GitHub Actions on push to `main`. See `.github/workflows/ci-cd.yml`.

---

## Documentation index

| Document | Purpose |
|---|---|
| `README.md` | This file — overview and quick start |
| `CONTRIBUTING.md` | How to contribute, branch strategy, code standards |
| `AGENT.md` | Instructions for AI coding agents |
| `docs/architecture.md` | Detailed architectural decisions and module design |
| `docs/modules/` | Per-module documentation |
| `docs/api/` | API endpoint reference |
| `docs/RUNNING.md` | Full local setup and troubleshooting |
| `docs/KEYCLOAK.md` | Keycloak configuration reference |
| `docs/POSTAL.md` | Postal SMTP setup guide |

---

## Project structure

```
banking-backend/
├── src/main/java/com/jjenus/banking/
│   ├── BankingApplication.java          ← entry point
│   ├── accounts/                         ← accounts module
│   │   ├── api/                          ← REST controllers
│   │   ├── application/                  ← application services
│   │   └── infrastructure/               ← JPA entities, port adapters
│   ├── transfers/                        ← transfers module
│   ├── ledger/                           ← ledger module
│   ├── notifications/                    ← notifications module
│   │   ├── listeners/                    ← @ApplicationModuleListener
│   │   └── service/                      ← EmailService → Postal SMTP
│   ├── audit/                            ← audit module
│   ├── reporting/                        ← reporting module
│   ├── identity/                         ← identity module
│   ├── admin/                            ← admin module
│   └── shared/                           ← config, exceptions, web utils
├── src/main/resources/
│   ├── application.yml                   ← main config
│   ├── application-local.yml             ← local dev overrides
│   ├── db/migration/                     ← Flyway SQL migrations
│   └── templates/email/                  ← Thymeleaf email templates
├── docker/
│   ├── postgres/init.sql                 ← DB initialisation
│   ├── keycloak/realm-banking.json       ← Keycloak realm export
│   ├── nginx/nginx.conf                  ← reverse proxy config
│   ├── prometheus/prometheus.yml         ← metrics scrape config
│   └── grafana/                          ← dashboard provisioning
├── .github/workflows/ci-cd.yml           ← GitHub Actions CI/CD
├── docker-compose.yml                    ← full local stack
├── Dockerfile                            ← multi-stage production build
├── AGENT.md                              ← AI agent instructions
├── CONTRIBUTING.md                       ← contributor guide
└── .env.example                          ← environment variable template
```

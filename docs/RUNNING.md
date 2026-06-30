# Running Locally

## Full setup (first time)

### Step 1: Install bank-core

The banking backend depends on `bank-core` which is not published to Maven Central. Install it into your local Maven repo:

```bash
git clone https://github.com/JJenus/bank-core.git ../bank-core
mvn install -f ../bank-core/pom.xml -DskipTests -B
```

Verify it installed: you should see `com.jjenus:bank-core:1.0.0` in `~/.m2/repository/com/jjenus/bank-core/`.

### Step 2: Configure environment

```bash
cp .env.example .env
```

The defaults in `.env.example` work for local development without changes. For production, replace every `changeme` and `change_me_in_production` value.

### Step 3: Start infrastructure

```bash
# Start Postgres, Redis, and Keycloak only
docker compose up -d postgres redis keycloak
```

**Wait for Keycloak.** On first boot it initialises the database and imports the `banking` realm — this takes 45–90 seconds. Check:

```bash
docker compose logs -f keycloak
# Wait until you see: "Running the server in development mode."
```

Or poll the health endpoint:
```bash
until curl -sf http://localhost:8180/realms/banking > /dev/null; do sleep 5; done && echo "Keycloak ready"
```

### Step 4: Run the application

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The app starts on port 8080. Flyway runs migrations automatically on startup.

### Step 5: Verify

```bash
# Health check
curl http://localhost:8080/api/actuator/health

# Swagger UI
open http://localhost:8080/api/swagger-ui.html

# Keycloak admin (admin / changeme)
open http://localhost:8180/admin
```

---

## Starting the full stack (with email and monitoring)

```bash
docker compose up -d
```

This starts all services including Postal SMTP, Prometheus, and Grafana.

**First-time Postal setup:**
Postal requires a one-time initialisation step. After starting:
```bash
docker exec -it banking-postal postal initialize
docker exec -it banking-postal postal make-user
```

Then open `http://localhost:5000` and complete the web UI setup.

---

## Creating a test user

1. Open Keycloak admin: `http://localhost:8180/admin` (admin / changeme)
2. Switch to the **banking** realm (top-left dropdown)
3. Users → **Add user**
4. Set `Email verified: ON`, fill in Username and Email
5. **Credentials tab** → Set password, toggle `Temporary: OFF`
6. **Role mappings tab** → Assign `CUSTOMER` (or `ADMIN` for full access)

### Getting a JWT for API testing

```bash
TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/banking/protocol/openid-connect/token \
  -d "client_id=banking-frontend" \
  -d "grant_type=password" \
  -d "username=YOUR_USERNAME" \
  -d "password=YOUR_PASSWORD" \
  | jq -r .access_token)

# Use the token
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/accounts/my
```

---

## Useful commands

```bash
# View running containers
docker compose ps

# Follow app logs
docker compose logs -f app

# Follow all logs
docker compose logs -f

# Restart app only (after code change)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Connect to Postgres
docker exec -it banking-postgres psql -U banking -d bank_db

# Connect to Redis
docker exec -it banking-redis redis-cli -a changeme

# Run all tests
./mvnw verify

# Run tests without integration tests (fast)
./mvnw test -P unit-tests

# Check module boundaries
./mvnw test -Dtest=ApplicationModulesTest

# Generate module documentation diagrams
./mvnw test -Dtest=ApplicationModulesTest#generateModuleDocs
# Output: target/modulith-docs/
```

---

## Troubleshooting

### App fails to start: "Connection refused" to PostgreSQL

Postgres is not ready yet. Wait for its health check:
```bash
docker compose ps postgres
# Status should be "healthy"
```

### App fails to start: "Connection refused" to Redis

Same as above:
```bash
docker compose ps redis
```

### App fails to start: JWT validation error

Keycloak is not ready or the realm hasn't been imported. Check:
```bash
curl http://localhost:8180/realms/banking
# Should return realm metadata JSON
```

If the realm is missing, check Keycloak logs:
```bash
docker compose logs keycloak | grep -i "error\|realm"
```

### "bank-core not found" during Maven build

Run the install step again:
```bash
mvn install -f ../bank-core/pom.xml -DskipTests
```

### Flyway migration error on startup

If you've changed a migration file (you shouldn't!), Flyway will refuse to run. Fix:
```bash
# In development only — reset the database
docker compose down -v postgres
docker compose up -d postgres
```

Or connect to Postgres and manually resolve the Flyway checksum:
```sql
DELETE FROM banking.flyway_schema_history WHERE version = 'X';
```

### Port already in use

Edit `.env` to change the conflicting port:
```bash
POSTGRES_PORT=5433
REDIS_PORT=6380
APP_PORT=8081
```

Then `docker compose up -d` again.

---

## IDE setup (IntelliJ IDEA)

1. Open the project root as a Maven project
2. Set the Project SDK to Java 17 (File → Project Structure → SDK)
3. Enable annotation processing (for MapStruct): Settings → Build → Compiler → Annotation Processors → Enable
4. Create a Run Configuration:
   - Main class: `com.jjenus.banking.BankingApplication`
   - VM options: `-Dspring.profiles.active=local`
   - Environment variables: copy from `.env`

-- docker/postgres/init.sql
-- Runs once when the PostgreSQL container is first created.
-- Creates the databases needed by each service.

CREATE DATABASE keycloak_db;
CREATE DATABASE postal_db;

-- bank_db is already created by the POSTGRES_DB environment variable.
-- Grant the banking user access to all databases.
GRANT ALL PRIVILEGES ON DATABASE bank_db      TO banking;
GRANT ALL PRIVILEGES ON DATABASE keycloak_db  TO banking;
GRANT ALL PRIVILEGES ON DATABASE postal_db    TO banking;

# Tracker (PostgreSQL)

Full **Tracker** REST API (auth, fitness, management, finance, logs, OpenAPI) backed by **PostgreSQL** with **Flyway** migrations. This project sits next to the Oracle **tracker** app (same parent folder) on port **9091** so it can run alongside the Oracle-backed server on **9090**.

## Prerequisites

- Java 17+
- Maven 3.9+
- Docker (for local Postgres and for integration tests)

## Database

From this directory:

```bash
docker compose up -d
```

Postgres listens on **localhost:5433** (avoids clashing with a local Postgres on 5432). Default credentials match `server/src/main/resources/application.yml`: user `tracker`, password `tracker`, database `tracker`.

## API server

```bash
cd server
mvn spring-boot:run
```

- Base URL: `http://localhost:9091`
- Health: `GET http://localhost:9091/actuator/health`
- API docs: `http://localhost:9091/swagger-ui.html` (same layout as the Oracle tracker)

Finance reads/writes the `robinhood_transactions` table created by Flyway (`V1__tracker_schema.sql`). CSV import and JDBC queries use PostgreSQL-compatible SQL (`LIMIT`, `to_timestamp`, etc.) in this module’s copy of `RobinhoodFinanceService`.

## Web UI (Angular)

This repo includes a copy of the Tracker Angular app under **`web/`**. Dev server **`proxy.conf.json`** forwards `/api` to **`http://127.0.0.1:9091`** (the tracker-pg API).

```bash
cd web
npm install
npm start
```

Then open `http://localhost:4200/`. You can still use the Oracle app’s `tracker/web` against port **9090** in parallel; this UI is scoped to the PostgreSQL stack.

## Tests

```bash
cd server && mvn test
```

Uses Testcontainers PostgreSQL when Docker is available. Without Docker, the context test is skipped (`@Testcontainers(disabledWithoutDocker = true)`).

## Cursor

**File → Open Folder…** and choose `tracker-pg` (this directory). To edit both apps, add the sibling `tracker` folder to the workspace.

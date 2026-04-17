# Tracker (PostgreSQL)

Full **Tracker** REST API (auth, fitness, management, finance, logs, OpenAPI) backed by **PostgreSQL** with **Flyway** migrations. This project sits next to the Oracle **tracker** app (same parent folder) on port **9091** so it can run alongside the Oracle-backed server on **9090**.

## Prerequisites

- Java 17+
- Maven 3.9+
- Docker (for local Postgres, optional API image, and integration tests)

## Database (Docker)

From the repo root, start **Postgres only** (recommended for day-to-day development):

```bash
docker compose up -d
```

Postgres listens on **localhost:5433** (avoids clashing with a local Postgres on 5432). Default credentials: user `tracker`, password `tracker`, database `tracker`. These match the defaults in `server/src/main/resources/application.yml`.

Stop and remove containers (data volume is kept unless you remove it explicitly):

```bash
docker compose down
```

## Local secrets (`application-local.yml`)

Do not commit real passwords or JWT secrets. Copy the template and edit the copy (the copy is gitignored):

```bash
cp server/src/main/resources/application-local.yml.example \
   server/src/main/resources/application-local.yml
```

Replace the `CHANGE_ME_*` placeholders and set Robinhood CSV paths if you use directory import.

Run the API with the **`local`** Spring profile so `application-local.yml` is loaded:

```bash
cd server
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

Without that profile, the server still starts using defaults from `application.yml` (fine for a quick try on an empty machine; not ideal for anything you treat as sensitive).

## API server (Maven, on the host)

With Postgres up (`docker compose up -d`):

```bash
cd server
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

- Base URL: `http://localhost:9091`
- Health: `GET http://localhost:9091/actuator/health`
- API docs: `http://localhost:9091/swagger-ui.html` (same layout as the Oracle tracker)

Finance reads/writes the `robinhood_transactions` table created by Flyway (`V1__tracker_schema.sql`). CSV import and JDBC queries use PostgreSQL-compatible SQL (`LIMIT`, `to_timestamp`, etc.) in this module’s copy of `RobinhoodFinanceService`.

## API server (Docker, optional)

If you want the JAR in a container instead of Maven on the host (still uses the same Compose Postgres service):

```bash
docker compose --profile api up -d --build
```

The API is exposed on **9091**. Do not run this at the same time as `mvn spring-boot:run` on the host unless you change one of the ports.

## Public / LAN access (Docker stack)

Use **`docker-compose.stack.yml`** for **Postgres + API + Angular** in Docker. Postgres has **no** host port; **API** and **web UI** are published via **`API_PORT`** and **`WEB_PORT`** in `.env.stack` (defaults **9091** and **9080**). Inside the stack the API always listens on **9091**; nginx in the **`web`** service proxies **`/api`** to `http://api:9091`, matching the production Angular build (`apiBaseUrl` is empty).

1. Copy the env template and set strong values (never commit `.env.stack`; it is gitignored):

```bash
cp .env.stack.example .env.stack
# edit .env.stack — passwords, JWT secret, bootstrap admin password, optional API_PORT / WEB_PORT / CORS_PATTERN
```

2. Build and start:

```bash
docker compose -f docker-compose.stack.yml --env-file .env.stack up -d --build
```

3. Open the UI at **`http://localhost:${WEB_PORT:-9080}/`** (or `http://<your-LAN-ip>:9080/`). API-only checks: **`http://localhost:${API_PORT:-9091}/actuator/health`**.

4. To reach services from the **public internet**, allow the chosen TCP ports through your **OS firewall** and usually **home router port forwarding**. Prefer **HTTPS** in front (Caddy, Traefik, or nginx) and tighten **`CORS_PATTERN`** when browsers hit the API from another origin instead of going through the bundled nginx UI.

**Security:** exposing any service to WAN increases risk. Use long random `POSTGRES_PASSWORD`, `TRACKER_AUTH_JWT_SECRET`, and `TRACKER_AUTH_PASSWORD_PEPPER`, keep the DB internal to Docker as in this stack file, and plan for TLS and rate limits for anything beyond a personal lab.

## Web UI (Angular)

This repo includes a copy of the Tracker Angular app under **`web/`**. Dev server **`web/proxy.conf.json`** forwards `/api` to **`http://127.0.0.1:9091`** (the tracker-pg API).

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

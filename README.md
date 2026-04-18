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

### Database lifecycle: one-time work vs every deploy

**One-time (operations / first environment):**

- Provision PostgreSQL (for example `docker compose up -d` or the stack file’s `postgres` service).

- **Copy or migrate data from the Oracle `tracker` application** using your own process (export/import, ETL, `pg_dump` from a staging DB, etc.). This repo does **not** run Oracle migration on each deploy. **Leave `auth_users` out** of any import (PostgreSQL app passwords use bcrypt + pepper + salt; Oracle hashes are incompatible).

- Set secrets (`application-local.yml`, `.env.stack`, or environment) and create non-bootstrap users as needed.

**Every deploy of the API or web container:**

- **Web:** ships static files only; it does **not** touch the database.
- **API:** **Flyway** runs on startup but applies **only pending** migration scripts under `server/src/main/resources/db/migration/`. Versions already recorded in `flyway_schema_history` are **not** re-run (no full schema replay from scratch).
- **Hibernate** uses `ddl-auto: validate`, so entities are checked against the schema; Hibernate does **not** auto-create or drop tables.

**Keeping data across deploys:**

- Postgres files live in a **named Docker volume** created by Compose (for example `tracker-pg_tracker_pg_data` or `tracker-pg_tracker_pg_stack_data`, depending on which file you use). Plain `docker compose down` **does not delete** that volume.
- **Avoid** `docker compose down -v` in environments where you care about data; `-v` removes named volumes declared in that compose project.

**Routine stack redeploy (API + web only, Postgres unchanged):**

```bash
ENV_FILE="${ENV_FILE:-.env.stack}"
docker compose -f docker-compose.stack.yml --env-file "$ENV_FILE" build api web
docker compose -f docker-compose.stack.yml --env-file "$ENV_FILE" up -d --no-deps api web
```

This rebuilds `api` and `web` and restarts them with `--no-deps` so the database container and its volume are not part of the recreate cycle.

## Reset the `admin` password in PostgreSQL

Passwords are **BCrypt** hashes of `password + "::" + salt + "::" + pepper`, matching `PasswordHashService` and **`TRACKER_AUTH_PASSWORD_PEPPER`** from your API environment (`.env.stack`, `application-local.yml`, or `application.yml`). You cannot set a plain-text password in SQL without that formula.

1. From **`server/`**, generate an `UPDATE` (use the **same pepper** the running API uses, or pass it as the second argument):

```bash
cd server
TRACKER_AUTH_PASSWORD_PEPPER='your-pepper-from-env' mvn -q compile exec:java \
  -Dexec.mainClass=com.svp.tracker.auth.tool.PasswordHashCli \
  "-Dexec.args=YourNewPassword"
```

2. Run the printed SQL in **DBeaver** or `psql` against the **`tracker`** database.

To reset another user, edit the `WHERE` clause in the printed SQL (or change `admin` in the tool source if you prefer a one-off).

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

Use **`docker-compose.stack.yml`** for **Postgres + API + Angular** in Docker. **Postgres** is mapped to **`127.0.0.1:${POSTGRES_HOST_PORT:-5433}`** on the host (not reachable from the internet on the instance’s public IP). **API** and **web** use **`API_PORT`** and **`WEB_PORT`**. Inside the stack the API listens on **9091**; nginx proxies **`/api`** to `http://api:9091`.

If **`docker compose up`** (dev Postgres on **5433**) is already running, set **`POSTGRES_HOST_PORT=5434`** in `.env.stack` for the stack to avoid a port bind conflict.

### Lightsail (Ubuntu), port 80, DBeaver from your laptop

**Lightsail networking → IPv4 firewall** for the instance: allow inbound **TCP 22** (SSH) and **TCP 80** only. Do **not** add a rule for Postgres (**5433** by default) or the API (**9091**) unless you explicitly need direct API access from the internet; the UI uses nginx on port **80** and proxies **`/api`** to the API.

In **`.env.stack`**, set **`WEB_PORT=80`**. Rebuild or recreate the stack so the web container is published on host port 80.

**DBeaver** (database not public): keep Postgres off the Lightsail firewall. From your laptop, open an **SSH tunnel** to the VM, then connect DBeaver to **localhost** on the forwarded port.

Terminal (replace host and key path):

```bash
ssh -N -L 5433:127.0.0.1:5433 ubuntu@YOUR_LIGHTSAIL_STATIC_IP -i ~/.ssh/your-lightsail-key.pem
```

Leave that session running. In DBeaver, new PostgreSQL connection: **Host** `127.0.0.1`, **Port** `5433`, **Database** / **User** / **Password** from `.env.stack` (`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`). Alternatively, use the **SSH** tab: enable “Use SSH Tunnel”, set the Lightsail host, user `ubuntu`, and your private key; on the **Main** tab set **Host** `localhost` and **Port** `5433` (host/port as seen **on the server** after the SSH session is established).

If you changed **`POSTGRES_HOST_PORT`** in `.env.stack`, use that value instead of **5433** in both the `ssh -L` command and DBeaver.

1. Copy the env template and set strong values (never commit `.env.stack`; it is gitignored):

```bash
cp .env.stack.example .env.stack
# edit .env.stack — passwords, JWT secret, bootstrap admin password, POSTGRES_HOST_PORT / API_PORT / WEB_PORT / CORS_PATTERN
```

2. **First-time or full stack** (Postgres + API + web):

```bash
docker compose -f docker-compose.stack.yml --env-file .env.stack up -d --build
```

After the database exists and is populated, use the **Routine stack redeploy** commands above for day-to-day API/web image updates so Postgres and its data volume stay put.

3. Open the UI at **`http://localhost:${WEB_PORT:-9080}/`** (or `http://<your-LAN-ip>:9080/`). API-only checks: **`http://localhost:${API_PORT:-9091}/actuator/health`**.

4. To reach services from the **public internet**, allow the chosen TCP ports through your **OS firewall** and usually **home router port forwarding**. Prefer **HTTPS** in front (Caddy, Traefik, or nginx) and tighten **`CORS_PATTERN`** when browsers hit the API from another origin instead of going through the bundled nginx UI. **Do not forward the Postgres port** to the internet unless you fully understand the risk; use VPN or SSH tunnel for remote DBeaver access.

**Security:** the stack binds Postgres to **127.0.0.1** on the VM only. Use a strong `POSTGRES_PASSWORD`, never add a public firewall rule for Postgres, and keep `TRACKER_AUTH_*` secrets long and random.

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

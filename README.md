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

- Postgres files live in a **named Docker volume** created by Compose (for example `tracker-pg_tracker_pg_data` or `tracker-pg_tracker_pg_stack_data`, depending on which file you use). The Compose **`name: tracker-pg`** in the YAML files pins the project name so merges and deploy scripts always target the same stack, even when `docker compose` is run from a parent directory. Plain `docker compose down` **does not delete** that volume.
- **Avoid** `docker compose down -v` in environments where you care about data; `-v` removes named volumes declared in that compose project.

**Routine stack redeploy (API + web only, Postgres unchanged):**

```bash
ENV_FILE="${ENV_FILE:-.env.stack}"
# Add -f docker-compose.https-lightsail.yml and `up -d --remove-orphans caddy` when using Caddy (or run scripts/lightsail-deploy.sh).
docker compose -f docker-compose.stack.yml --env-file "$ENV_FILE" build api web
docker compose -f docker-compose.stack.yml --env-file "$ENV_FILE" up -d --no-deps --force-recreate --remove-orphans api web
```

This rebuilds `api` and `web` and restarts them with `--no-deps` so the database container and its volume are not part of the recreate cycle. **`--remove-orphans`** drops services you removed from the compose merge (for example **Caddy** after turning HTTPS off). **`--force-recreate`** avoids stale **`tracker-pg-api-1`** name conflicts when a previous container was left behind. If you use **HTTPS (Caddy)**, prefer **`bash scripts/lightsail-deploy.sh`** on the host so the correct merge files and **`caddy`** are applied.

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

## Create or update a user script

Use the helper script to create or update a login in PostgreSQL.

```bash
bash scripts/create-demo-user.sh
```

Defaults:

- username: `demo`
- password: `demo123`
- role: `USER`
- MFA: `false` (so login works without SMS setup)

You can override values:

```bash
bash scripts/create-demo-user.sh demo DemoPass123 USER false true
```

The script reads `.env.stack` when present for DB connection and `TRACKER_AUTH_PASSWORD_PEPPER`, then applies an upsert into `auth_users` keyed by username.

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

Use **`docker-compose.stack.yml`** for **Postgres + API + Angular** in Docker. **Postgres** is mapped to **`127.0.0.1:${POSTGRES_HOST_PORT:-5433}`** on the host (not reachable from the internet on the instance’s public IP). **API** and **web** use **`API_PORT`** and **`WEB_PORT_BIND`** (a Docker port spec such as `9080:80` or `127.0.0.1:9080:80`). Inside the stack the API listens on **9091**; nginx proxies **`/api`** to `http://api:9091`.

If **`docker compose up`** (dev Postgres on **5433**) is already running, set **`POSTGRES_HOST_PORT=5434`** in `.env.stack` for the stack to avoid a port bind conflict.

### Lightsail (Ubuntu), port 80, DBeaver from your laptop

**If `docker compose build api` freezes or drops SSH:** the Maven step needs a lot of RAM. Prefer a plan with **at least 2GB RAM** for in-place Docker builds, or add **1–2GB swap** on Ubuntu (`sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile`). You can also set **`MAVEN_BUILD_HEAP=512m`** in **`.env.stack`** (see **`.env.stack.example`**) to cap the build JVM.

**Lightsail networking → IPv4 firewall** for the instance: allow **TCP 22** (SSH) and, for plain HTTP, **TCP 80** (and **443** if you use HTTPS in front; see the next section). Do **not** add a rule for Postgres (**5433** by default) or the API (**9091**) unless you explicitly need direct API access from the internet; the public UI is normally nginx (and, with Caddy, TLS on 443) proxying **`/api`** to the API.

In **`.env.stack`**, set **`WEB_PORT_BIND=80:80`** for **HTTP** on the host (URL `http://<instance-ip>/` with no port in the path). Rebuild or recreate the stack so the `web` container is published on host port 80.

### Lightsail HTTPS (Caddy, Let’s Encrypt)

To serve the app at **`https://your-domain`**, add a **public DNS A record** for your domain pointing at the **Lightsail static IP**, and open **TCP 80** and **443** in the Lightsail firewall (80 is used for the ACME HTTP-01 challenge).

1. In **`.env.stack`**, set **`CADDY_DOMAIN=your.fqdn.example.com`**, and bind the nginx `web` service only to localhost so Caddy can own **80** and **443** on the host:

   - **`WEB_PORT_BIND=127.0.0.1:9080:80`**

2. Set **`CORS_PATTERN`** to your site origin, e.g. **`https://your.fqdn.example.com`**, so the browser and API agree on HTTPS.

3. Start (or first-time) the stack with both compose files, or use the deploy script with Caddy enabled:

   ```bash
   docker compose -f docker-compose.stack.yml -f docker-compose.https-lightsail.yml --env-file .env.stack up -d --build --remove-orphans
   ```

4. Ongoing **GitHub Actions** deploys: set variable **`LIGHTSAIL_USE_CADDY`** to **`1`**, or on the host run **`export TRACKER_CADDY=1`**, or **`touch .use-caddy-lightsail`** in the repo root. **`scripts/lightsail-deploy.sh`** will merge **`docker-compose.https-lightsail.yml`** and run **`caddy`** after **api** / **web**.

Caddy is configured in **`deploy/caddy/Caddyfile`**; certificates are stored in the **`caddy_data`** Compose volume. The Spring API and nginx are written to trust **`X-Forwarded-Proto`** from the edge so links and CORS see **HTTPS** correctly.

**DBeaver** (database not public): keep Postgres off the Lightsail firewall. From your laptop, open an **SSH tunnel** to the VM, then connect DBeaver to **localhost** on the forwarded port.

Terminal (replace host and key path):

```bash
ssh -N -L 5433:127.0.0.1:5433 ubuntu@YOUR_LIGHTSAIL_STATIC_IP -i ~/.ssh/your-lightsail-key.pem
```

Leave that session running. In DBeaver, new PostgreSQL connection: **Host** `127.0.0.1`, **Port** `5433`, **Database** / **User** / **Password** from `.env.stack` (`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`). Alternatively, use the **SSH** tab: enable “Use SSH Tunnel”, set the Lightsail host, user `ubuntu`, and your private key; on the **Main** tab set **Host** `localhost` and **Port** `5433` (host/port as seen **on the server** after the SSH session is established).

If you changed **`POSTGRES_HOST_PORT`** in `.env.stack`, use that value instead of **5433** in both the `ssh -L` command and DBeaver.

**Robinhood CSV directory import on Lightsail:** the API only sees filesystem paths **inside** its container. Your laptop’s `/Users/...` paths do not exist there. On the Ubuntu instance create directories (for example under `/home/ubuntu/robinhood/reports/`), copy `*.csv` into the import folder, then run the stack with the optional merge file so those host folders are mounted at `/robinhood/import` and `/robinhood/uploaded`:

```bash
mkdir -p /home/ubuntu/robinhood/reports/import /home/ubuntu/robinhood/reports/uploaded
docker compose -f docker-compose.stack.yml -f docker-compose.robinhood.yml --env-file .env.stack up -d --build --remove-orphans
```

**Deploy script (`scripts/lightsail-deploy.sh`):** after `git pull`, run `bash scripts/lightsail-deploy.sh` to rebuild **api** and **web** (and start **caddy** when Caddy is enabled) without recreating **postgres**. To always merge **`docker-compose.robinhood.yml`** on that host, either run `export TRACKER_ROBINHOOD_COMPOSE=1` or once: `touch .use-lightsail-robinhood-compose` in the repo root (gitignored). For **Caddy/HTTPS**, use **`TRACKER_CADDY=1`** or **`touch .use-caddy-lightsail`**. In GitHub Actions, set **`LIGHTSAIL_USE_ROBINHOOD_COMPOSE`** and/or **`LIGHTSAIL_USE_CADDY`** to **`1`** as needed.

For **`mvn spring-boot:run`** on your Mac, use the **`local`** profile and set `tracker.finance.robinhood-csv-import-directory` / `robinhood-csv-uploaded-directory` in gitignored **`application-local.yml`** (see `application-local.yml.example`).

**Finance stock alerts:** users create alert rules in **Finance → Alerts** (target price or session % rise, one-time or repeat with cooldown). Recipient provisioning lives in **Admin → Finance**. Outbound provider secrets stay in environment / `.env.stack`, not in the UI:

- Email: set **`TRACKER_FINANCE_ALERTS_EMAIL_ENABLED=true`**, **`TRACKER_FINANCE_ALERTS_EMAIL_FROM`**, plus Spring Mail settings such as **`SPRING_MAIL_HOST`**, **`SPRING_MAIL_PORT`**, **`SPRING_MAIL_USERNAME`**, **`SPRING_MAIL_PASSWORD`**, **`SPRING_MAIL_SMTP_AUTH`**, and **`SPRING_MAIL_SMTP_STARTTLS_ENABLE`**.
- SMS: set **`TRACKER_FINANCE_ALERTS_SMS_ENABLED=true`**, **`TRACKER_FINANCE_ALERTS_TWILIO_ACCOUNT_SID`**, **`TRACKER_FINANCE_ALERTS_TWILIO_AUTH_TOKEN`**, and **`TRACKER_FINANCE_ALERTS_TWILIO_FROM_NUMBER`**.
- Polling is controlled by **`TRACKER_FINANCE_ALERTS_EVALUATION_ENABLED`** and **`TRACKER_FINANCE_ALERTS_POLL_FIXED_DELAY_MS`**. Quotes are Yahoo best-effort and may be delayed.

1. Copy the env template and set strong values (never commit `.env.stack`; it is gitignored):

```bash
cp .env.stack.example .env.stack
# edit .env.stack — passwords, JWT secret, bootstrap admin password, POSTGRES_HOST_PORT / API_PORT / WEB_PORT_BIND / CORS_PATTERN (CADDY_DOMAIN when using HTTPS, TRACKER_JOURNAL_S* for S3 journal files)
```

2. **First-time or full stack** (Postgres + API + web):

```bash
docker compose -f docker-compose.stack.yml --env-file .env.stack up -d --build --remove-orphans
```

After the database exists and is populated, use the **Routine stack redeploy** commands above for day-to-day API/web image updates so Postgres and its data volume stay put.

3. Open the UI at **`http://localhost:9080/`** when **`WEB_PORT_BIND=9080:80`** (or use the host port you chose in the left side of the mapping, e.g. `http://localhost:80/` for `WEB_PORT_BIND=80:80`). API-only checks: **`http://localhost:${API_PORT:-9091}/actuator/health`**.

4. To reach services from the **public internet**, allow the chosen TCP ports through your **OS firewall** and usually **home router port forwarding**. Prefer **HTTPS** in front (Caddy, Traefik, or nginx) and tighten **`CORS_PATTERN`** when browsers hit the API from another origin instead of going through the bundled nginx UI. **Do not forward the Postgres port** to the internet unless you fully understand the risk; use VPN or SSH tunnel for remote DBeaver access.

**Journal attachments:** by default, uploaded files are stored on disk (see `TRACKER_JOURNAL_STORAGE_DIR` / tmp). For production, set **`TRACKER_JOURNAL_S3_BUCKET=tracker-pg-journal`** (and optionally **`TRACKER_JOURNAL_S3_REGION`**) in **`.env.stack`** so the API uses **Amazon S3** with the [default AWS credential chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials.html) (e.g. Lightsail/EC2 instance role, or `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` on the host). Create the bucket and grant the API `s3:PutObject`, `s3:GetObject`, and `s3:DeleteObject` on that bucket. Metadata stays in PostgreSQL; object keys in the `journal_attachments` table are either the legacy on-disk filename or S3 object keys of the form `journal/{userId}/{entryId}/{uuid}`.

**Security:** the stack binds Postgres to **127.0.0.1** on the VM only. Use a strong `POSTGRES_PASSWORD`, never add a public firewall rule for Postgres, and keep `TRACKER_AUTH_*` secrets long and random.

## Web UI (Angular)

This repo includes a copy of the Tracker Angular app under **`web/`**. Dev server **`web/proxy.conf.json`** forwards `/api` to **`http://127.0.0.1:9091`** (the tracker-pg API).

```bash
cd web
npm install
npm start
```

Then open `http://localhost:4200/`. You can still use the Oracle app’s `tracker/web` against port **9090** in parallel; this UI is scoped to the PostgreSQL stack.

### Main tabs

The top navigation includes dedicated sections for:

- Exercise
- Finance
- Management (tasks/calendar)
- Journal (Markdown entries, calendar heatmap, tags, attachments)
- Reports
- Admin
- Logs (admin only)

## GitHub Actions deploy to Lightsail

Workflow: `.github/workflows/deploy-develop-lightsail.yml` (name: **Deploy pushed branch to Lightsail**)

Trigger behavior:

- Runs on **push to any branch** (`"**"`).
- SSHes into the Lightsail host and checks out/resets to the same pushed branch.
- Runs `bash scripts/lightsail-deploy.sh` on the VM.

### Secrets/variables used by the workflow

Set these in **GitHub → Repo → Settings → Secrets and variables → Actions**:

- **Required secrets**
  - `LIGHTSAIL_HOST` (public IP or DNS)
  - `LIGHTSAIL_SSH_KEY` (private PEM key content)
  - `LIGHTSAIL_USER` (optional; defaults to `ubuntu` if omitted)

- **Optional repo variable**
  - `LIGHTSAIL_REPO_DIR` (absolute repo path on Lightsail VM)
  - Default used by workflow when unset: `/home/ubuntu/apps/tracker-pg`

If your repo is cloned elsewhere on the instance (example `/home/ubuntu/tracker-pg`), set `LIGHTSAIL_REPO_DIR` to that exact path.

## Tests

```bash
cd server && mvn test
```

Uses Testcontainers PostgreSQL when Docker is available. Without Docker, the context test is skipped (`@Testcontainers(disabledWithoutDocker = true)`).

## Cursor

**File → Open Folder…** and choose `tracker-pg` (this directory). To edit both apps, add the sibling `tracker` folder to the workspace.

# Import Oracle `tracker` data into PostgreSQL `tracker-pg` (leave `auth_users`)

Goal: copy **business tables** from the Oracle Tracker app into this project’s PostgreSQL schema, **without** replacing **`auth_users`** (and without relying on Oracle user ids for MFA / trusted locations).

## 1. Prerequisites

- PostgreSQL `tracker` database reachable (e.g. DBeaver on `localhost:5433` or stack `POSTGRES_HOST_PORT`).
- Oracle Tracker schema access (same logical tables as `server/src/main/resources/db/migration/V1__tracker_schema.sql`).
- Table/column names on Oracle may be **uppercase**; PostgreSQL uses **lowercase** unquoted identifiers. Adjust exports or use quoted identifiers in `COPY` as needed.

## 2. Clear existing non-auth data in Postgres

Run **`pg-truncate-non-auth.sql`** once before import (or after a failed attempt). This:

- **Does not** touch `auth_users`.
- Removes `auth_mfa_challenges` and `auth_trusted_locations` (recommended empty state after Oracle import).
- Truncates management, fitness, Robinhood tables and resets their `BIGSERIAL` ids unless you choose to preserve Oracle ids (advanced).

## 3. Export from Oracle (exclude auth users)

Use any of:

- **DBeaver / Oracle SQL Developer**: right‑click table → Export → CSV, for each table listed in **`oracle-export-list.sql`** (uncomment and run per table).
- **Oracle Data Pump** (`expdp` / `impdp`) with `INCLUDE=TABLE` lists that omit `AUTH_USERS`.
- **ora2pg** with an `EXCLUDE` on `AUTH_USERS` (and optionally `AUTH_TRUSTED_LOCATIONS`, `AUTH_MFA_CHALLENGES`).

**Do not import** `AUTH_USERS`: Postgres passwords use **bcrypt + pepper + salt**; Oracle hashes are incompatible.

**Recommended:** do **not** import `AUTH_TRUSTED_LOCATIONS` or `AUTH_MFA_CHALLENGES` (they reference `user_id` values from Oracle that will not match your Postgres `auth_users.id`).

## 4. Load into PostgreSQL

Typical order (respect foreign keys):

1. `management_task_categories`
2. `management_task_types`
3. `management_tasks`
4. `fitness_exercises`
5. `fitness_exercise_day_logs`
6. `fitness_body_weight`
7. `robinhood_transactions`

Use **psql `\copy`** from CSV, or DBeaver “Import data”, or `COPY ... FROM STDIN` with matching column order.

### Robinhood / timestamps

Map Oracle `DATE`/`TIMESTAMP` columns to PostgreSQL `TIMESTAMP` / `TIMESTAMPTZ` as in `V1__tracker_schema.sql`. If Oracle uses different column names, rename in CSV or use a staging table + `INSERT ... SELECT` with expressions.

### Sequences after manual `INSERT` with explicit ids

If you insert explicit `id` values from Oracle, reset sequences, for example:

```sql
SELECT setval(pg_get_serial_sequence('management_task_categories', 'id'),
              COALESCE((SELECT MAX(id) FROM management_task_categories), 1));
```

(repeat per `BIGSERIAL` table as needed).

## 5. Verify

- Log in with your **existing** Postgres `admin` (or other) user.
- Exercise / management / finance screens should show migrated rows.

## 6. Re-run import

Run **`pg-truncate-non-auth.sql`** again, then re-import. This does **not** remove `auth_users`.

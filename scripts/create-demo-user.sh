#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
server_dir="${repo_root}/server"

show_usage() {
  cat <<'EOF'
Usage:
  bash scripts/create-demo-user.sh [username] [password] [role] [mfa_enabled] [active] [phone_e164]

Examples:
  bash scripts/create-demo-user.sh
  bash scripts/create-demo-user.sh demo DemoPass123 USER false true
  bash scripts/create-demo-user.sh alice 'A#Strong#Pass9' ADMIN false true +15551234567
  bash scripts/demo-user.sh
  bash scripts/demo-user.sh 'NishaSecurePass9' ADMIN

Defaults:
  username=demo
  password=demo123
  role=USER
  mfa_enabled=false
  active=true
  phone_e164=-

Notes:
  - This script upserts by username (creates new users or updates existing users).
  - Reads .env.stack (when present) for DB settings and TRACKER_AUTH_PASSWORD_PEPPER.
  - The generated hash must use the same password pepper and BCrypt strength as the API (defaults match
    application.yml: pepper tracker-dev-pepper when TRACKER_AUTH_PASSWORD_PEPPER is unset, strength 12). If login fails
    after creating a user, re-run this script after pulling the fix, or set TRACKER_AUTH_PASSWORD_PEPPER in .env.stack
    to match the server and run again.
  - Do not set TRACKER_AUTH_PASSWORD_PEPPER to an empty value in .env.stack unless the API uses an empty pepper too;
    an empty line used to force a CLI/API mismatch (fixed by unsetting the variable before mvn when blank).
  - Password is passed via env TRACKER_UPSERT_PASSWORD (not in -Dexec.args) so characters like $ in the password are
    not interpreted by the shell.
  - If the stack's postgres container is running, applies SQL via `docker compose exec -iT` (stdin must stay open for
    `psql -f -`; without `-i`, no SQL runs and the script still exits 0).
  - Otherwise uses host psql when a real PostgreSQL client is installed (not Ubuntu's stub).
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  show_usage
  exit 0
fi

username="${1:-demo}"
password="${2:-demo123}"
role="${3:-USER}"
mfa_enabled="${4:-false}"
active="${5:-true}"
phone_e164="${6:--}"

dotenv_file="${repo_root}/.env.stack"

dotenv_get() {
  local key="$1"
  local default_value="$2"
  local value=""
  if [[ -f "${dotenv_file}" ]]; then
    value="$(
      awk -v k="${key}" '
        $0 ~ "^[[:space:]]*" k "=" {
          line = $0
          sub(/^[[:space:]]*[^=]*=/, "", line)
          gsub(/^[[:space:]]+|[[:space:]]+$/, "", line)
          if ((line ~ /^".*"$/) || (line ~ /^'\''.*'\''$/)) {
            line = substr(line, 2, length(line) - 2)
          }
          print line
          exit
        }
      ' "${dotenv_file}"
    )"
  fi
  if [[ -n "${value}" ]]; then
    printf '%s' "${value}"
  else
    printf '%s' "${default_value}"
  fi
}

postgres_host="${POSTGRES_HOST:-$(dotenv_get POSTGRES_HOST 127.0.0.1)}"
postgres_port="${POSTGRES_HOST_PORT:-$(dotenv_get POSTGRES_HOST_PORT 5433)}"
postgres_db="${POSTGRES_DB:-$(dotenv_get POSTGRES_DB tracker)}"
postgres_user="${POSTGRES_USER:-$(dotenv_get POSTGRES_USER tracker)}"
postgres_password="${POSTGRES_PASSWORD:-$(dotenv_get POSTGRES_PASSWORD tracker)}"
auth_pepper="${TRACKER_AUTH_PASSWORD_PEPPER:-$(dotenv_get TRACKER_AUTH_PASSWORD_PEPPER "")}"

tmp_sql="$(mktemp)"
trap 'rm -f "${tmp_sql}"' EXIT

echo "Generating user upsert SQL for '${username}'..."
(
  cd "${server_dir}"
  # If we export TRACKER_AUTH_PASSWORD_PEPPER="", the JVM sees an empty pepper; the API defaults to
  # tracker-dev-pepper when the variable is absent. Unset when blank so the CLI matches the server.
  unset TRACKER_AUTH_PASSWORD_PEPPER 2>/dev/null || true
  if [[ -n "${auth_pepper// }" ]]; then
    export TRACKER_AUTH_PASSWORD_PEPPER="${auth_pepper}"
  fi
  # Password must not appear in -Dexec.args: bash expands $ and other characters inside double quotes, which breaks
  # Maven/Java args. UserUpsertSqlCli reads TRACKER_UPSERT_PASSWORD when set (see server UserUpsertSqlCli).
  unset TRACKER_UPSERT_PASSWORD 2>/dev/null || true
  export TRACKER_UPSERT_PASSWORD="${password}"
  mvn -q compile exec:java \
    -Dexec.mainClass=com.svp.tracker.auth.tool.UserUpsertSqlCli \
    "-Dexec.args=${username} ${role} ${mfa_enabled} ${active} ${phone_e164}"
) > "${tmp_sql}"

if ! grep -q "INSERT INTO auth_users" "${tmp_sql}"; then
  echo "Error: Maven did not produce an INSERT for auth_users (empty or unexpected output)." >&2
  cat "${tmp_sql}" >&2
  exit 1
fi

# Ubuntu may ship a psql stub (postgresql-client-common) that errors until postgresql-client-* is installed.
psql_client_ready() {
  command -v psql >/dev/null 2>&1 || return 1
  local ver
  ver="$(psql --version 2>/dev/null || true)"
  [[ "${ver}" =~ PostgreSQL ]] || return 1
  return 0
}

dc_stack() {
  if [[ -f "${dotenv_file}" ]]; then
    docker compose --env-file "${dotenv_file}" -f "${repo_root}/docker-compose.stack.yml" "$@"
  else
    docker compose -f "${repo_root}/docker-compose.stack.yml" "$@"
  fi
}

postgres_container_running() {
  command -v docker >/dev/null 2>&1 || return 1
  dc_stack ps --status running -q postgres 2>/dev/null | grep -q .
}

sql_escape_literal() {
  printf '%s' "$1" | sed "s/'/''/g"
}

# How we reach Postgres for this run (fixed for apply + verify so we never switch compose vs host mid-script).
pg_via_compose_exec=false
if postgres_container_running; then
  pg_via_compose_exec=true
elif ! psql_client_ready && command -v docker >/dev/null 2>&1; then
  pg_via_compose_exec=true
fi

# Run psql against the same target chosen for apply.
psql_apply_or_query() {
  if [[ "${pg_via_compose_exec}" == "true" ]]; then
    dc_stack exec -iT \
      -e PGPASSWORD="${postgres_password}" \
      postgres \
      psql \
      -U "${postgres_user}" \
      -d "${postgres_db}" \
      -v ON_ERROR_STOP=1 \
      "$@"
  elif psql_client_ready; then
    PGPASSWORD="${postgres_password}" psql \
      -h "${postgres_host}" \
      -p "${postgres_port}" \
      -U "${postgres_user}" \
      -d "${postgres_db}" \
      -v ON_ERROR_STOP=1 \
      "$@"
  else
    return 1
  fi
}

echo "Applying SQL to PostgreSQL ${postgres_host}:${postgres_port}/${postgres_db}..."
if [[ "${pg_via_compose_exec}" == "true" ]]; then
  if postgres_container_running; then
    echo "Using docker compose postgres service (container running)..."
  else
    echo "Local psql not usable; trying docker compose postgres service..." >&2
  fi
  psql_apply_or_query -f - < "${tmp_sql}"
elif psql_client_ready; then
  psql_apply_or_query -f "${tmp_sql}"
else
  echo "Error: no working psql and docker is not on PATH." >&2
  echo "Either: sudo apt install -y postgresql-client" >&2
  echo "Or: start the stack (docker compose ... up) so this script can use the postgres container's psql." >&2
  exit 1
fi

u_esc="$(sql_escape_literal "${username}")"
verify_sql="SELECT COUNT(*)::text FROM auth_users WHERE LOWER(TRIM(username)) = LOWER(TRIM('${u_esc}'));"
row_count="$(psql_apply_or_query -At -c "${verify_sql}" | tr -d '\r\n')"
if [[ "${row_count}" != "1" ]]; then
  echo "Error: expected exactly 1 auth_users row for username '${username}' after apply; got count='${row_count}'." >&2
  echo "Check that this script targets the same database as the API (see .env.stack POSTGRES_* and compose project)." >&2
  exit 1
fi

echo "Done."
echo "User upsert complete:"
echo "  username=${username}"
echo "  role=${role}"
echo "  mfa_enabled=${mfa_enabled}"
echo "  active=${active}"
echo "  Sign in with that username and the password you passed as the 2nd argument (defaults: demo→demo123; scripts/demo-user.sh → nisha123)."


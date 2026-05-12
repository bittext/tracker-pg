#!/usr/bin/env bash
# Change a user's role in auth_users (ADMIN | USER) — same Docker / .env.stack / psql wiring as drop-user.sh.
#
# Usage:
#   bash scripts/set-user-role.sh                    # ayush -> ADMIN (defaults)
#   bash scripts/set-user-role.sh ayush ADMIN
#   bash scripts/set-user-role.sh nisha USER
#   bash scripts/set-user-role.sh --help
#
# Notes:
#   - JWTs are stateless: the affected user must sign OUT and back IN for the new role to take effect in the browser.
#   - Role values are matched against AppUserRole.name() (case-insensitive on the script side, stored upper-case).
#   - The script aborts when the username does not match exactly one auth_users row.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

show_usage() {
  cat <<'EOF'
Usage:
  bash scripts/set-user-role.sh [username] [role]

Defaults: username=ayush, role=ADMIN
Valid roles: ADMIN, USER (case-insensitive)

One-liner (stack + .env.stack; adjust user/db/password from your env file):
  docker compose --env-file .env.stack -f docker-compose.stack.yml exec -T \\
    -e PGPASSWORD="$POSTGRES_PASSWORD" postgres \\
    psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 \\
    -c "UPDATE auth_users SET role = 'ADMIN' WHERE LOWER(TRIM(username)) = LOWER('ayush');"

Notes:
  - JWTs are stateless: the user must sign out and back in for the new role to apply in the browser.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  show_usage
  exit 0
fi

username="${1:-ayush}"
role_in="${2:-ADMIN}"

if [[ -z "${username// }" ]]; then
  echo "username cannot be empty" >&2
  exit 1
fi

role_upper="$(printf '%s' "${role_in}" | tr '[:lower:]' '[:upper:]' | tr -d '[:space:]')"
case "${role_upper}" in
  ADMIN|USER) ;;
  *)
    echo "role must be ADMIN or USER (got '${role_in}')" >&2
    exit 1
    ;;
esac

sql_escape() {
  printf '%s' "$1" | sed "s/'/''/g"
}
u="$(sql_escape "${username}")"
r="$(sql_escape "${role_upper}")"

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

tmp_sql="$(mktemp)"
trap 'rm -f "${tmp_sql}"' EXIT

# Single transaction: assert the user exists exactly once, then update. RAISE EXCEPTION rolls the txn back if missing.
cat >"${tmp_sql}" <<SQL
BEGIN;
DO
\$\$
DECLARE
    matched INTEGER;
    previous_role TEXT;
BEGIN
    SELECT COUNT(*) INTO matched FROM auth_users
      WHERE LOWER(TRIM(username)) = LOWER('${u}');
    IF matched = 0 THEN
        RAISE EXCEPTION 'No auth_users row for username = %', '${u}';
    ELSIF matched > 1 THEN
        RAISE EXCEPTION 'Ambiguous match: % rows for username = %', matched, '${u}';
    END IF;
    SELECT role INTO previous_role FROM auth_users
      WHERE LOWER(TRIM(username)) = LOWER('${u}');
    UPDATE auth_users SET role = '${r}'
      WHERE LOWER(TRIM(username)) = LOWER('${u}');
    RAISE NOTICE 'auth_users: % role changed from % to %', '${u}', previous_role, '${r}';
END
\$\$;
COMMIT;
SELECT id, username, role, active FROM auth_users WHERE LOWER(TRIM(username)) = LOWER('${u}');
SQL

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

echo "Setting role='${role_upper}' for user '${username}' on ${postgres_host}:${postgres_port}/${postgres_db} ..."
if postgres_container_running; then
  dc_stack exec -iT \
    -e PGPASSWORD="${postgres_password}" \
    postgres \
    psql \
    -U "${postgres_user}" \
    -d "${postgres_db}" \
    -v ON_ERROR_STOP=1 \
    -f - <"${tmp_sql}"
elif psql_client_ready; then
  PGPASSWORD="${postgres_password}" psql \
    -h "${postgres_host}" \
    -p "${postgres_port}" \
    -U "${postgres_user}" \
    -d "${postgres_db}" \
    -v ON_ERROR_STOP=1 \
    -f - <"${tmp_sql}"
elif command -v docker >/dev/null 2>&1; then
  dc_stack exec -iT \
    -e PGPASSWORD="${postgres_password}" \
    postgres \
    psql \
    -U "${postgres_user}" \
    -d "${postgres_db}" \
    -v ON_ERROR_STOP=1 \
    -f - <"${tmp_sql}"
else
  echo "Error: no working psql and docker is not on PATH." >&2
  exit 1
fi

echo "Done. NOTE: '${username}' must sign out and back in for the new role to be reflected in the browser JWT."

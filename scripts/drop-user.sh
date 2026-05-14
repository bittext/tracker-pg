#!/usr/bin/env bash
# Remove a login from auth_users (same Docker / .env.stack / psql wiring as scripts/set-user-role.sh and scripts/add-user.sh).
# Clears auth_mfa_challenges and auth_trusted_locations for that user first so delete usually succeeds.
#
# Usage:
#   bash scripts/drop-user.sh
#   bash scripts/drop-user.sh nisha
#   bash scripts/drop-user.sh --help
#
# If DELETE fails because this user still owns rows (management_tasks, journal, etc.), remove or reassign those
# rows first, or use a dedicated DB maintenance path.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

show_usage() {
  cat <<'EOF'
Usage:
  bash scripts/drop-user.sh [username]

Default username: nisha

One-liner (stack + .env.stack; adjust user/db/password from your env file):
  docker compose --env-file .env.stack -f docker-compose.stack.yml exec -T \\
    -e PGPASSWORD="$POSTGRES_PASSWORD" postgres \\
    psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 \\
    -c "BEGIN;
        DELETE FROM auth_mfa_challenges WHERE user_id = (SELECT id FROM auth_users WHERE LOWER(username) = LOWER('nisha'));
        DELETE FROM auth_trusted_locations WHERE user_id = (SELECT id FROM auth_users WHERE LOWER(username) = LOWER('nisha'));
        DELETE FROM auth_users WHERE LOWER(username) = LOWER('nisha');
        COMMIT;"

Notes:
  - member_profiles rows for that user are removed by ON DELETE CASCADE when auth_users is deleted.
  - Other tables with owner_user_id → auth_users may still block the delete until those rows are gone or reassigned.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  show_usage
  exit 0
fi

username="${1:-nisha}"
if [[ -z "${username// }" ]]; then
  echo "username cannot be empty" >&2
  exit 1
fi

sql_escape() {
  printf '%s' "$1" | sed "s/'/''/g"
}
u="$(sql_escape "${username}")"

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

cat >"${tmp_sql}" <<SQL
BEGIN;
DELETE FROM auth_mfa_challenges
  WHERE user_id = (SELECT id FROM auth_users WHERE LOWER(username) = LOWER('${u}'));
DELETE FROM auth_trusted_locations
  WHERE user_id = (SELECT id FROM auth_users WHERE LOWER(username) = LOWER('${u}'));
DELETE FROM auth_users WHERE LOWER(username) = LOWER('${u}');
COMMIT;
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

echo "Deleting user '${username}' from ${postgres_host}:${postgres_port}/${postgres_db} ..."
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

echo "Done (user '${username}' removed if a matching row existed)."

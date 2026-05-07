#!/usr/bin/env bash
# Delete Plaid-generated import rows + related transactions + all Plaid link rows.
# Keeps manual uploads and institution rows.
#
# Usage:
#   bash scripts/clear-plaid-imports-and-links.sh
#   bash scripts/clear-plaid-imports-and-links.sh --yes

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
sql_file="${repo_root}/scripts/clear-plaid-imports-and-links.sql"
dotenv_file="${repo_root}/.env.stack"

show_usage() {
  cat <<'USAGE'
Usage:
  bash scripts/clear-plaid-imports-and-links.sh [--yes]

  --yes   Skip confirmation prompt.

Deletes:
  - banking_import_files rows created by Plaid (path/name/parse_note match)
  - banking_transactions linked to those files
  - all banking_plaid_items (linked Plaid connections)

Does NOT delete:
  - manual banking import rows/files
  - banking institutions

Do not run the .sql file with bash.
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  show_usage
  exit 0
fi

skip_confirm=false
if [[ "${1:-}" == "--yes" ]]; then
  skip_confirm=true
fi

if [[ ! -f "${sql_file}" ]]; then
  echo "Missing ${sql_file}" >&2
  exit 1
fi

if [[ "${skip_confirm}" != "true" ]]; then
  read -r -p "Delete ALL Plaid imports, related transactions, and links? Type yes: " ans
  if [[ "${ans}" != "yes" ]]; then
    echo "Aborted." >&2
    exit 1
  fi
fi

if [[ -n "${DATABASE_URL:-}" ]]; then
  psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f "${sql_file}"
  echo "Done."
  exit 0
fi

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

echo "Deleting Plaid imports and links via ${postgres_host}:${postgres_port}/${postgres_db} ..."
if postgres_container_running; then
  dc_stack exec -iT \
    -e PGPASSWORD="${postgres_password}" \
    postgres \
    psql \
    -U "${postgres_user}" \
    -d "${postgres_db}" \
    -v ON_ERROR_STOP=1 \
    -f - <"${sql_file}"
elif psql_client_ready; then
  PGPASSWORD="${postgres_password}" psql \
    -h "${postgres_host}" \
    -p "${postgres_port}" \
    -U "${postgres_user}" \
    -d "${postgres_db}" \
    -v ON_ERROR_STOP=1 \
    -f "${sql_file}"
elif command -v docker >/dev/null 2>&1; then
  dc_stack exec -iT \
    -e PGPASSWORD="${postgres_password}" \
    postgres \
    psql \
    -U "${postgres_user}" \
    -d "${postgres_db}" \
    -v ON_ERROR_STOP=1 \
    -f - <"${sql_file}"
else
  echo "Error: set DATABASE_URL, or install psql, or run Docker stack postgres." >&2
  exit 1
fi

echo "Done."

#!/usr/bin/env bash
# Clean up robinhood_transactions (duplicates, malformed imports, year delete, or full truncate).
#
# Usage:
#   bash scripts/cleanup-robinhood-transactions.sh --preview --year 2026
#   bash scripts/cleanup-robinhood-transactions.sh --dedupe --year 2026
#   bash scripts/cleanup-robinhood-transactions.sh --malformed --year 2026 --yes
#   bash scripts/cleanup-robinhood-transactions.sh --delete-year --year 2026 --yes
#   bash scripts/cleanup-robinhood-transactions.sh --truncate --yes
#   bash scripts/cleanup-robinhood-transactions.sh --dedupe --year 2026 --user mylogin --yes
#
# Connection (first match wins):
#   DATABASE_URL, or docker compose postgres / host psql via .env.stack (same as clear-banking-import-data.sh)

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
sql_file="${repo_root}/scripts/cleanup-robinhood-transactions.sql"
dotenv_file="${repo_root}/.env.stack"

action="preview"
year="$(date +%Y)"
owner_username=""
skip_confirm=false

show_usage() {
  cat <<'EOF'
Usage:
  bash scripts/cleanup-robinhood-transactions.sh [options]

Options (pick one action):
  --preview       Show counts only (default). No deletes.
  --dedupe        Remove exact duplicate rows in the year scope (keeps one per import key).
  --malformed     Delete rows whose trans_code is numeric (CSV column-shift corruption).
  --delete-year   Delete all rows in --year scope (use before a clean CSV re-import).
  --truncate      Delete all rows (all users) or only --user if set.

Other:
  --year YYYY     Calendar year for preview/dedupe/malformed/delete-year (default: current year).
  --user NAME     Limit to auth_users.username (optional).
  --yes           Skip confirmation for destructive actions.
  -h, --help      This help.

Examples:
  bash scripts/cleanup-robinhood-transactions.sh --preview --year 2026
  bash scripts/cleanup-robinhood-transactions.sh --dedupe --year 2026 --yes
  bash scripts/cleanup-robinhood-transactions.sh --malformed --year 2026 --yes
  bash scripts/cleanup-robinhood-transactions.sh --delete-year --year 2026 --yes
  # Then re-import CSV in Admin → Finance → Robinhood

SQL only:
  psql "$DATABASE_URL" -v ON_ERROR_STOP=1 \
    -v action=preview -v year=2026 -v owner_username= \
    -f scripts/cleanup-robinhood-transactions.sql
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --preview) action="preview" ;;
    --dedupe) action="dedupe" ;;
    --malformed) action="malformed" ;;
    --delete-year) action="delete_year" ;;
    --truncate) action="truncate" ;;
    --year)
      shift
      year="${1:?--year requires YYYY}"
      ;;
    --user)
      shift
      owner_username="${1:?--user requires username}"
      ;;
    --yes) skip_confirm=true ;;
    -h | --help)
      show_usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      show_usage >&2
      exit 1
      ;;
  esac
  shift
done

if [[ ! "${year}" =~ ^[0-9]{4}$ ]]; then
  echo "Invalid --year: ${year} (use YYYY)" >&2
  exit 1
fi

if [[ ! -f "${sql_file}" ]]; then
  echo "Missing ${sql_file}" >&2
  exit 1
fi

destructive=false
if [[ "${action}" != "preview" ]]; then
  destructive=true
fi

if [[ "${destructive}" == "true" && "${skip_confirm}" != "true" ]]; then
  scope="year ${year}"
  if [[ -n "${owner_username}" ]]; then
    scope="${scope}, user ${owner_username}"
  fi
  if [[ "${action}" == "truncate" && -z "${owner_username}" ]]; then
    scope="ALL users, entire robinhood_transactions table"
  fi
  read -r -p "Run action '${action}' (${scope})? Type yes: " ans
  if [[ "${ans}" != "yes" ]]; then
    echo "Aborted." >&2
    exit 1
  fi
fi

run_psql() {
  local -a psql_vars=(
    -v ON_ERROR_STOP=1
    -v "action=${action}"
    -v "year=${year}"
    -v "owner_username=${owner_username}"
  )

  # Pipe SQL on stdin for docker exec — the postgres container cannot read host paths.
  run_psql_via_docker() {
    dc_stack exec -iT \
      -e PGPASSWORD="${postgres_password}" \
      postgres \
      psql \
      -U "${postgres_user}" \
      -d "${postgres_db}" \
      "${psql_vars[@]}" \
      -f - <"${sql_file}"
  }

  if [[ -n "${DATABASE_URL:-}" ]]; then
    psql "$DATABASE_URL" "${psql_vars[@]}" -f "${sql_file}"
    return
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

  local postgres_host="${POSTGRES_HOST:-$(dotenv_get POSTGRES_HOST 127.0.0.1)}"
  local postgres_port="${POSTGRES_HOST_PORT:-$(dotenv_get POSTGRES_HOST_PORT 5433)}"
  local postgres_db="${POSTGRES_DB:-$(dotenv_get POSTGRES_DB tracker)}"
  local postgres_user="${POSTGRES_USER:-$(dotenv_get POSTGRES_USER tracker)}"
  local postgres_password="${POSTGRES_PASSWORD:-$(dotenv_get POSTGRES_PASSWORD tracker)}"

  psql_client_ready() {
    command -v psql >/dev/null 2>&1 || return 1
    local ver
    ver="$(psql --version 2>/dev/null || true)"
    [[ "${ver}" =~ PostgreSQL ]] || return 1
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

  if postgres_container_running; then
    run_psql_via_docker
  elif psql_client_ready; then
    PGPASSWORD="${postgres_password}" psql \
      -h "${postgres_host}" \
      -p "${postgres_port}" \
      -U "${postgres_user}" \
      -d "${postgres_db}" \
      "${psql_vars[@]}" \
      -f "${sql_file}"
  elif command -v docker >/dev/null 2>&1; then
    run_psql_via_docker
  else
    echo "Error: set DATABASE_URL, or install psql, or run Docker stack postgres." >&2
    exit 1
  fi
}

echo "Robinhood cleanup: action=${action} year=${year} user=${owner_username:-<all>}"
run_psql
echo "Done."

#!/usr/bin/env bash
# Create or update a row in auth_users (INSERT ... ON CONFLICT DO UPDATE) using the same BCrypt rules
# as the running app (UserUpsertSqlCli). Safe to run repeatedly: re-runs refresh password_hash / salt
# and other fields for that username.
#
# Requires: JDK + Maven on PATH (same machine you run this from). Reads TRACKER_AUTH_PASSWORD_PEPPER
# from the environment or from .env.stack so hashes match production.
#
# Usage:
#   bash scripts/add-user.sh --help
#   bash scripts/add-user.sh <username> [<password>] [role] [mfa_enabled] [active] [phone_e164]
#
# If <password> is omitted and TRACKER_UPSERT_PASSWORD is unset, you are prompted twice (hidden).
# Prefer TRACKER_UPSERT_PASSWORD so the password never appears in argv or shell history:
#
#   TRACKER_UPSERT_PASSWORD='your-secret' bash scripts/add-user.sh alice ADMIN false true
#
# phone_e164: optional E.164 like +15551234567, or "-" for NULL (default "-").
#
# DB target: same Docker / .env.stack / psql wiring as scripts/set-user-role.sh.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
server_pom="${repo_root}/server/pom.xml"
dotenv_file="${repo_root}/.env.stack"

show_usage() {
  cat <<'EOF'
Usage:
  bash scripts/add-user.sh <username> [<password>] [role] [mfa_enabled] [active] [phone_e164]

  role:          ADMIN or USER (default USER)
  mfa_enabled:   true|false (default false)
  active:        true|false (default true)
  phone_e164:    optional; use "-" for blank (default "-")

Password (pick one):
  - Pass as the 2nd argument (discouraged: visible in process list / shell history), or
  - Set TRACKER_UPSERT_PASSWORD in the environment (recommended), or
  - Omit both: you will be prompted twice (hidden input).

Examples:
  TRACKER_UPSERT_PASSWORD='S3cure!' bash scripts/add-user.sh bob USER false true
  bash scripts/add-user.sh carol 'Tmp#Pass9' ADMIN false true -
  bash scripts/add-user.sh dana   # prompts for password

Requires Maven + JDK. Pepper: export TRACKER_AUTH_PASSWORD_PEPPER or define it in .env.stack
(TRACKER_AUTH_PASSWORD_PEPPER=...) so hashes match the API container.

DB connection uses .env.stack when present (POSTGRES_*, POSTGRES_HOST, POSTGRES_HOST_PORT), or
docker compose exec postgres when the stack is running; otherwise local psql to host:port.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" || $# -eq 0 ]]; then
  show_usage
  [[ $# -eq 0 ]] && exit 1
  exit 0
fi

username_raw="$1"
shift

if [[ -z "${username_raw// }" ]]; then
  echo "username cannot be empty" >&2
  exit 1
fi

username_lc="$(printf '%s' "${username_raw}" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"
# SQL string literal for the trailing SELECT (username_lc is normalized but may contain quotes).
username_sql_escaped="$(printf '%s' "${username_lc}" | sed "s/'/''/g")"

role="USER"
mfa="false"
active="true"
phone="-"

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

# Pepper: shell env wins, else .env.stack, else leave unset (Java uses application default).
pepper_effective="${TRACKER_AUTH_PASSWORD_PEPPER-}"
if [[ -z "${pepper_effective}" && -f "${dotenv_file}" ]]; then
  pepper_effective="$(dotenv_get TRACKER_AUTH_PASSWORD_PEPPER "")"
fi

# --- password into TRACKER_UPSERT_PASSWORD (never log it) ---
unset_pw_after=0
if [[ -n "${TRACKER_UPSERT_PASSWORD:-}" ]]; then
  :
elif [[ $# -ge 1 && "${1}" != "ADMIN" && "${1}" != "USER" && "${1}" != "admin" && "${1}" != "user" ]]; then
  # First remaining token is the password (heuristic: not a role keyword).
  export TRACKER_UPSERT_PASSWORD="$1"
  shift
  unset_pw_after=1
elif [[ $# -ge 1 && ("${1}" == "ADMIN" || "${1}" == "USER" || "${1}" == "admin" || "${1}" == "user") ]]; then
  echo "TRACKER_UPSERT_PASSWORD is not set and no password was given before role='${1}'." >&2
  echo "Set TRACKER_UPSERT_PASSWORD, pass the password as the 2nd argument, or run without extra args to be prompted." >&2
  exit 1
else
  p1=""
  p2=""
  read -rsp "Password for '${username_lc}': " p1
  echo "" >&2
  read -rsp "Confirm password: " p2
  echo "" >&2
  if [[ "${p1}" != "${p2}" ]]; then
    echo "Passwords do not match." >&2
    exit 1
  fi
  if [[ -z "${p1}" ]]; then
    echo "Password cannot be empty." >&2
    exit 1
  fi
  export TRACKER_UPSERT_PASSWORD="${p1}"
  unset_pw_after=1
fi

# Remaining positional: role [mfa] [active] [phone]
if [[ $# -ge 1 ]]; then
  role="$1"
  shift
fi
if [[ $# -ge 1 ]]; then
  mfa="$1"
  shift
fi
if [[ $# -ge 1 ]]; then
  active="$1"
  shift
fi
if [[ $# -ge 1 ]]; then
  phone="$1"
  shift
fi
if [[ $# -ge 1 ]]; then
  echo "Unexpected extra arguments: $*" >&2
  exit 1
fi

role_upper="$(printf '%s' "${role}" | tr '[:lower:]' '[:upper:]' | tr -d '[:space:]')"
case "${role_upper}" in
  ADMIN|USER) ;;
  *)
    echo "role must be ADMIN or USER (got '${role}')" >&2
    exit 1
    ;;
esac

case "$(printf '%s' "${mfa}" | tr '[:upper:]' '[:lower:]')" in
  true|false) ;;
  *)
    echo "mfa_enabled must be true or false (got '${mfa}')" >&2
    exit 1
    ;;
esac

case "$(printf '%s' "${active}" | tr '[:upper:]' '[:lower:]')" in
  true|false) ;;
  *)
    echo "active must be true or false (got '${active}')" >&2
    exit 1
    ;;
esac

if ! command -v mvn >/dev/null 2>&1; then
  echo "Error: mvn is not on PATH. Install Maven (or use a dev machine), then re-run this script." >&2
  echo "The script itself is reusable; it invokes UserUpsertSqlCli to generate SQL matching PasswordHashService." >&2
  exit 1
fi

if [[ ! -f "${server_pom}" ]]; then
  echo "Error: missing ${server_pom}" >&2
  exit 1
fi

tmp_sql="$(mktemp)"
trap 'rm -f "${tmp_sql}"; if [[ "${unset_pw_after:-0}" -eq 1 ]]; then unset TRACKER_UPSERT_PASSWORD || true; fi' EXIT

# Build exec.args: username role mfa active phone [pepper optional — let Java read pepper from env]
exec_args="${username_lc} ${role_upper} ${mfa} ${active} ${phone}"

echo "Generating upsert SQL via UserUpsertSqlCli (Maven) …" >&2
(
  cd "${repo_root}/server"
  export TRACKER_UPSERT_PASSWORD
  if [[ -n "${pepper_effective}" ]]; then
    export TRACKER_AUTH_PASSWORD_PEPPER="${pepper_effective}"
  fi
  # Optional: match API if you tune bcrypt strength in .env.stack
  bcrypt_from_env="${TRACKER_AUTH_BCRYPT_STRENGTH-}"
  if [[ -z "${bcrypt_from_env}" && -f "${dotenv_file}" ]]; then
    bcrypt_from_env="$(dotenv_get TRACKER_AUTH_BCRYPT_STRENGTH "")"
  fi
  if [[ -n "${bcrypt_from_env}" ]]; then
    export TRACKER_AUTH_BCRYPT_STRENGTH="${bcrypt_from_env}"
  fi
  mvn -q -B -f "${server_pom}" compile exec:java \
    -Dexec.mainClass=com.svp.tracker.auth.tool.UserUpsertSqlCli \
    -D"exec.args=${exec_args}" >"${tmp_sql}" 2>/tmp/add-user-mvn.err || {
    echo "Maven failed. stderr:" >&2
    cat /tmp/add-user-mvn.err >&2
    exit 1
  }
)

if ! grep -q "INSERT INTO auth_users" "${tmp_sql}"; then
  echo "Error: generated SQL does not contain expected INSERT. Contents:" >&2
  cat "${tmp_sql}" >&2
  exit 1
fi

{
  echo "BEGIN;"
  cat "${tmp_sql}"
  echo "SELECT id, username, role, active, mfa_enabled, phone_e164 FROM auth_users WHERE LOWER(TRIM(username)) = LOWER('${username_sql_escaped}');"
  echo "COMMIT;"
} >"${tmp_sql}.wrapped"
mv "${tmp_sql}.wrapped" "${tmp_sql}"

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

echo "Applying upsert on ${postgres_host}:${postgres_port}/${postgres_db} as ${postgres_user} …" >&2
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

echo "Done. User '${username_lc}' is ready to sign in (JWT is stateless; no server restart needed)."

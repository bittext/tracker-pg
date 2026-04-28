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
  TRACKER_AUTH_PASSWORD_PEPPER="${auth_pepper}" mvn -q compile exec:java \
    -Dexec.mainClass=com.svp.tracker.auth.tool.UserUpsertSqlCli \
    "-Dexec.args=${username} ${password} ${role} ${mfa_enabled} ${active} ${phone_e164}"
) > "${tmp_sql}"

echo "Applying SQL to PostgreSQL ${postgres_host}:${postgres_port}/${postgres_db}..."
PGPASSWORD="${postgres_password}" psql \
  -h "${postgres_host}" \
  -p "${postgres_port}" \
  -U "${postgres_user}" \
  -d "${postgres_db}" \
  -v ON_ERROR_STOP=1 \
  -f "${tmp_sql}"

echo "Done."
echo "User upsert complete:"
echo "  username=${username}"
echo "  role=${role}"
echo "  mfa_enabled=${mfa_enabled}"
echo "  active=${active}"

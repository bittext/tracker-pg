#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
server_dir="${repo_root}/server"

username="${1:-demo}"
password="${2:-demo123}"
role="${3:-USER}"
mfa_enabled="${4:-false}"
active="${5:-true}"
phone_e164="${6:--}"

if [[ -f "${repo_root}/.env.stack" ]]; then
  # shellcheck disable=SC1091
  source "${repo_root}/.env.stack"
fi

postgres_host="${POSTGRES_HOST:-127.0.0.1}"
postgres_port="${POSTGRES_HOST_PORT:-5433}"
postgres_db="${POSTGRES_DB:-tracker}"
postgres_user="${POSTGRES_USER:-tracker}"
postgres_password="${POSTGRES_PASSWORD:-tracker}"
auth_pepper="${TRACKER_AUTH_PASSWORD_PEPPER:-}"

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
echo "Demo user ready:"
echo "  username=${username}"
echo "  role=${role}"
echo "  mfa_enabled=${mfa_enabled}"

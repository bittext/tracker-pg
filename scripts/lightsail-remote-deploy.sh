#!/usr/bin/env bash
# Dump PostgreSQL data from your local machine (same DB you use in DBeaver), then optionally
# upload the SQL file to Ubuntu Lightsail and apply it inside the stack's postgres container.
#
# Prerequisites (local): pg_dump (e.g. brew install libpq && brew link --force libpq), bash 4+, ssh, scp
# Prerequisites (Lightsail): repo at LIGHTSAIL_REPO_DIR with docker compose stack running; .env.stack present
#
# Local connection — same as DBeaver (pick one):
#   export DATABASE_URL="postgresql://USER:PASSWORD@127.0.0.1:5433/tracker"
#   or: PGHOST PGPORT PGUSER PGPASSWORD PGDATABASE
#
# Remote / SSH:
#   export LIGHTSAIL_HOST="203.0.113.50"
#   export LIGHTSAIL_SSH_KEY="$HOME/.ssh/LightsailDefaultKey-us-east-1.pem"
#   optional: LIGHTSAIL_USER (default ubuntu), LIGHTSAIL_REPO_DIR (default /home/ubuntu/apps/tracker-pg)
#   optional: REMOTE_POSTGRES_USER REMOTE_POSTGRES_DB (default tracker / tracker)
#
# Flyway: local flyway_schema_history is excluded so AWS keeps its own migration history.
#
# Usage:
#   ./scripts/lightsail-remote-deploy.sh dump
#   ./scripts/lightsail-remote-deploy.sh upload dumps/pg-data-....sql
#   ./scripts/lightsail-remote-deploy.sh apply dumps/pg-data-....sql
#   ./scripts/lightsail-remote-deploy.sh sync    # dump, upload that file, apply it
#
# If target tables already have rows, COPY may fail on primary keys; truncate or clear those tables
# on AWS first (respect FK order), or load into an empty database after Flyway has created the schema.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
dumps_dir="${repo_root}/dumps"
mkdir -p "${dumps_dir}"

LIGHTSAIL_USER="${LIGHTSAIL_USER:-ubuntu}"
LIGHTSAIL_REPO_DIR="${LIGHTSAIL_REPO_DIR:-/home/ubuntu/apps/tracker-pg}"
REMOTE_POSTGRES_USER="${REMOTE_POSTGRES_USER:-tracker}"
REMOTE_POSTGRES_DB="${REMOTE_POSTGRES_DB:-tracker}"
TRACKER_ENV_FILE="${TRACKER_ENV_FILE:-.env.stack}"

require_pg_dump() {
  if ! command -v pg_dump >/dev/null 2>&1; then
    echo "pg_dump not found. Install PostgreSQL client tools (e.g. macOS: brew install libpq && brew link --force libpq)." >&2
    exit 1
  fi
}

require_local_db() {
  if [[ -n "${DATABASE_URL:-}" ]]; then
    return 0
  fi
  if [[ -n "${PGHOST:-}" || -n "${PGDATABASE:-}" ]]; then
    return 0
  fi
  echo "Set DATABASE_URL or PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE for the local database (DBeaver connection)." >&2
  exit 1
}

require_ssh() {
  [[ -n "${LIGHTSAIL_HOST:-}" ]] || {
    echo "Set LIGHTSAIL_HOST to the instance public IP or DNS." >&2
    exit 1
  }
  [[ -n "${LIGHTSAIL_SSH_KEY:-}" ]] || {
    echo "Set LIGHTSAIL_SSH_KEY to the path of your Lightsail .pem private key." >&2
    exit 1
  }
  [[ -f "${LIGHTSAIL_SSH_KEY}" ]] || {
    echo "LIGHTSAIL_SSH_KEY file not found: ${LIGHTSAIL_SSH_KEY}" >&2
    exit 1
  }
}

ssh_base() {
  ssh -o StrictHostKeyChecking=accept-new -i "${LIGHTSAIL_SSH_KEY}" "${LIGHTSAIL_USER}@${LIGHTSAIL_HOST}" "$@"
}

scp_base() {
  scp -o StrictHostKeyChecking=accept-new -i "${LIGHTSAIL_SSH_KEY}" "$@"
}

cmd_dump() {
  require_pg_dump
  require_local_db
  local out="${dumps_dir}/pg-data-$(date +%Y%m%d-%H%M%S).sql"
  echo "Writing ${out} ..."
  # Exclude Flyway history so the target keeps its own flyway_schema_history (set PGDUMP_EXCLUDE_FLYWAY=0 if pg_dump errors).
  local exclude=()
  if [[ "${PGDUMP_EXCLUDE_FLYWAY:-1}" != "0" ]]; then
    exclude+=(--exclude-table=public.flyway_schema_history)
  fi
  if [[ -n "${DATABASE_URL:-}" ]]; then
    pg_dump "${DATABASE_URL}" \
      --data-only --no-owner --no-acl \
      "${exclude[@]}" \
      -f "${out}"
  else
    pg_dump \
      --data-only --no-owner --no-acl \
      "${exclude[@]}" \
      -f "${out}"
  fi
  echo "Done. Next: $0 upload ${out} && $0 apply ${out}   or: $0 sync"
}

cmd_upload() {
  require_ssh
  local f="${1:?usage: $0 upload /path/to/dump.sql}"
  [[ -f "$f" ]] || {
    echo "File not found: $f" >&2
    exit 1
  }
  local base
  base="$(basename "$f")"
  echo "Uploading to ${LIGHTSAIL_HOST}:${LIGHTSAIL_REPO_DIR}/dumps/${base} ..."
  ssh_base "mkdir -p '${LIGHTSAIL_REPO_DIR}/dumps'"
  scp_base "$f" "${LIGHTSAIL_USER}@${LIGHTSAIL_HOST}:${LIGHTSAIL_REPO_DIR}/dumps/${base}"
  echo "Uploaded."
}

cmd_apply() {
  require_ssh
  local base
  base="$(basename "${1:?usage: $0 apply /path/to/dump.sql or dumps/....sql}")"
  local remote_sql="${LIGHTSAIL_REPO_DIR}/dumps/${base}"
  echo "Applying on ${LIGHTSAIL_HOST} via docker compose postgres (ON_ERROR_STOP) ..."
  # SQL path is on the VM host; stream it into psql inside the container (not mounted in the image).
  ssh_base "cd '${LIGHTSAIL_REPO_DIR}' && test -f '${TRACKER_ENV_FILE}' && test -f '${remote_sql}' && cat '${remote_sql}' | docker compose -f docker-compose.stack.yml --env-file '${TRACKER_ENV_FILE}' exec -iT postgres psql -U '${REMOTE_POSTGRES_USER}' -d '${REMOTE_POSTGRES_DB}' -v ON_ERROR_STOP=1 -f -"
  echo "Apply finished."
}

cmd_sync() {
  cmd_dump
  local latest
  latest="$(ls -t "${dumps_dir}"/pg-data-*.sql 2>/dev/null | head -1)"
  [[ -n "$latest" ]] || {
    echo "No dump file found under ${dumps_dir}" >&2
    exit 1
  }
  cmd_upload "$latest"
  cmd_apply "$latest"
}

usage() {
  echo "Usage: $0 dump | upload <file.sql> | apply <file.sql> | sync" >&2
  exit 1
}

case "${1:-}" in
dump) cmd_dump ;;
upload) cmd_upload "${2:-}" ;;
apply) cmd_apply "${2:-}" ;;
sync) cmd_sync ;;
*) usage ;;
esac

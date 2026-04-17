#!/usr/bin/env bash
# Load scripts/oracle-to-pg/.env.migration (see .env.migration.example), then run JDBC Oracle → PostgreSQL copy.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="$SCRIPT_DIR/.env.migration"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE — copy .env.migration.example, fill Oracle + Postgres credentials, then re-run." >&2
  exit 1
fi
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a
cd "$ROOT/server"
mvn -q compile exec:java -Dexec.mainClass=com.svp.tracker.migration.OracleToPostgresCopy

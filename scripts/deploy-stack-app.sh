#!/usr/bin/env bash
# Rebuild and restart only the API and web containers. Postgres is left running and its
# Docker volume is not removed — use this for routine app deploys after the DB exists.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
ENV_FILE="${ENV_FILE:-.env.stack}"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing ${ENV_FILE}. Copy .env.stack.example and edit, or set ENV_FILE=..." >&2
  exit 1
fi
COMPOSE=(docker compose -f docker-compose.stack.yml --env-file "$ENV_FILE")
"${COMPOSE[@]}" build api web
"${COMPOSE[@]}" up -d --no-deps api web
echo "API and web updated. Postgres unchanged (data volume intact)."

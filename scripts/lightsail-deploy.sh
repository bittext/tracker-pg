#!/usr/bin/env bash
# Run on the Lightsail host from the repository root after `git pull` (see GitHub Actions deploy workflow).
# Rebuilds and restarts only api + web; does not recreate or restart postgres (--no-deps).
# First-time stack (Postgres + volumes): run once from repo root:
#   docker compose -f docker-compose.stack.yml --env-file .env.stack up -d --build
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"
env_file="${TRACKER_ENV_FILE:-.env.stack}"
if [[ ! -f "$env_file" ]]; then
  echo "Missing ${env_file}. On the server: cp .env.stack.example .env.stack && edit secrets." >&2
  exit 1
fi
docker compose -f docker-compose.stack.yml --env-file "$env_file" build api web
docker compose -f docker-compose.stack.yml --env-file "$env_file" up -d --no-deps api web

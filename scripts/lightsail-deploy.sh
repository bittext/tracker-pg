#!/usr/bin/env bash
# Run on the Lightsail host from the repository root after `git pull` (see GitHub Actions deploy workflow).
# Rebuilds and restarts only api + web; does not recreate or restart postgres (--no-deps).
# First-time stack (Postgres + volumes): run once from repo root:
#   docker compose -f docker-compose.stack.yml --env-file .env.stack up -d --build
#
# Robinhood directory import (bind mounts + import/upload paths): either
#   export TRACKER_ROBINHOOD_COMPOSE=1
# or create a marker file (gitignored):  touch .use-lightsail-robinhood-compose
# then this script also merges docker-compose.robinhood.yml.
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"
env_file="${TRACKER_ENV_FILE:-.env.stack}"
if [[ ! -f "$env_file" ]]; then
  echo "Missing ${env_file}. On the server: cp .env.stack.example .env.stack && edit secrets." >&2
  exit 1
fi

compose_files=( -f docker-compose.stack.yml )
if [[ "${TRACKER_ROBINHOOD_COMPOSE:-0}" == "1" ]] || [[ -f "${repo_root}/.use-lightsail-robinhood-compose" ]]; then
  if [[ -f "${repo_root}/docker-compose.robinhood.yml" ]]; then
    compose_files+=( -f docker-compose.robinhood.yml )
    echo "Including docker-compose.robinhood.yml (Robinhood CSV directory mounts)."
  else
    echo "WARN: Robinhood compose requested but docker-compose.robinhood.yml not found; using stack only." >&2
  fi
fi

docker compose "${compose_files[@]}" --env-file "$env_file" build api web
docker compose "${compose_files[@]}" --env-file "$env_file" up -d --no-deps api web

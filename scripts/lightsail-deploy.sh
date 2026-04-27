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
#
# Caddy (HTTPS) on public 80/443 is merged when any of:
#   - export TRACKER_CADDY=1, or touch .use-caddy-lightsail, or
#   - CADDY_DOMAIN=your.fqdn is set in .env.stack (auto-detect so deploys do not drop Caddy as an orphan).
# To disable auto-merge while keeping CADDY_DOMAIN in the file, set TRACKER_CADDY_DISABLE=1 in .env.stack.
# Requires CADDY_DOMAIN and usually WEB_PORT_BIND=127.0.0.1:9080:80 in .env.stack.
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

# Merge Caddy into the *same* compose project as api/web. If Caddy is omitted, --remove-orphans (below) removes
# the running caddy container because it is no longer part of the project definition.
use_caddy=0
if [[ "${TRACKER_CADDY:-0}" == "1" ]] || [[ -f "${repo_root}/.use-caddy-lightsail" ]]; then
  use_caddy=1
  echo "Including docker-compose.https-lightsail.yml (TRACKER_CADDY=1 or .use-caddy-lightsail)."
elif [[ -f "$env_file" ]]; then
  if grep -qE '^[[:space:]]*TRACKER_CADDY_DISABLE=1' "$env_file" 2>/dev/null; then
    echo "Caddy auto-merge disabled (TRACKER_CADDY_DISABLE=1 in ${env_file})."
  elif grep -qE '^[[:space:]]*CADDY_DOMAIN=[^#[:space:]]' "$env_file" 2>/dev/null; then
    use_caddy=1
    echo "Including docker-compose.https-lightsail.yml (CADDY_DOMAIN is set in ${env_file})."
  fi
fi
if [[ "$use_caddy" -eq 1 ]]; then
  if [[ -f "${repo_root}/docker-compose.https-lightsail.yml" ]]; then
    compose_files+=( -f docker-compose.https-lightsail.yml )
  else
    echo "WARN: Caddy requested but docker-compose.https-lightsail.yml not found; Caddy will not be started." >&2
    use_caddy=0
  fi
fi

# --remove-orphans: dropping Caddy or Robinhood overlays no longer leaves old containers (e.g. tracker-pg-caddy-1).
# --force-recreate: avoids "container name already in use" when a prior run left a stale api/web container.
docker compose "${compose_files[@]}" --env-file "$env_file" build api web
docker compose "${compose_files[@]}" --env-file "$env_file" up -d --no-deps --force-recreate --remove-orphans api web
# Ensure Caddy (re)starts and stays in the project; picks up Caddyfile bind-mount changes.
if [[ "$use_caddy" -eq 1 ]] && [[ -f "${repo_root}/docker-compose.https-lightsail.yml" ]]; then
  docker compose "${compose_files[@]}" --env-file "$env_file" up -d caddy
fi

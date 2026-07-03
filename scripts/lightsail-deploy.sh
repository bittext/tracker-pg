#!/usr/bin/env bash
# Run on the Lightsail host from the repository root after `git pull` (see GitHub Actions deploy workflow).
# Rebuilds and restarts api + web (+ robinhood-notebook when enabled); does not recreate postgres (--no-deps).
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
#
# Robinhood Agentic sidecar (Finance → Robinhood live sync): build/start robinhood-agent when any of:
#   - export TRACKER_ROBINHOOD_AGENTIC=1, or touch .use-lightsail-robinhood-agent, or
#   - TRACKER_FINANCE_ROBINHOOD_AGENTIC_ENABLED=true in .env.stack (auto-detect).
# Disable auto-detect while keeping the flag in .env.stack: TRACKER_ROBINHOOD_AGENTIC_DISABLE=1
# Or touch .use-lightsail-robinhood-agent on the server to force-start the sidecar.
#   - export TRACKER_ROBINHOOD_NOTEBOOK=1, or touch .use-lightsail-robinhood-notebook, or
#   - TRACKER_FINANCE_ROBINHOOD_NOTEBOOK_SERVICE_ENABLED=true in .env.stack (auto-detect).
# Disable auto-detect while keeping the flag in .env.stack: TRACKER_ROBINHOOD_NOTEBOOK_DISABLE=1
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"
env_file="${TRACKER_ENV_FILE:-.env.stack}"
compose_project=( -p tracker-pg )

# Case-insensitive: true | 1 | yes | on (optional trailing comment).
env_var_enabled() {
  local key="$1"
  [[ -f "$env_file" ]] && grep -qiE "^[[:space:]]*${key}=(true|1|yes|on)([[:space:]#]|$)" "$env_file" 2>/dev/null
}
if [[ ! -f "$env_file" ]]; then
  echo "Missing ${env_file}. On the server: cp .env.stack.example .env.stack && edit secrets." >&2
  exit 1
fi

# Remove hash-prefixed stale containers (e.g. 1c480efcaea9_tracker-pg-api-1) left by interrupted compose runs.
remove_stale_compose_containers() {
  local service="$1"
  local canonical="tracker-pg-${service}-1"
  local id name
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    id="${line%% *}"
    name="${line#* }"
    name="${name#/}"
    if [[ "$name" == *"tracker-pg-${service}-"* ]] && [[ "$name" != "$canonical" ]]; then
      echo "Removing stale container ${name} (${id})..."
      docker rm -f "$id" >/dev/null 2>&1 || true
    fi
  done < <(docker ps -a --filter "name=tracker-pg-${service}-" --format '{{.ID}} {{.Names}}' 2>/dev/null || true)
}

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

use_robinhood_agent=0
if [[ "${TRACKER_ROBINHOOD_AGENTIC:-0}" == "1" ]] || [[ -f "${repo_root}/.use-lightsail-robinhood-agent" ]]; then
  use_robinhood_agent=1
  echo "Including robinhood-agent service (TRACKER_ROBINHOOD_AGENTIC=1 or .use-lightsail-robinhood-agent)."
elif grep -qE '^[[:space:]]*TRACKER_ROBINHOOD_AGENTIC_DISABLE=1' "$env_file" 2>/dev/null; then
  echo "robinhood-agent auto-start disabled (TRACKER_ROBINHOOD_AGENTIC_DISABLE=1 in ${env_file})."
elif env_var_enabled TRACKER_FINANCE_ROBINHOOD_AGENTIC_ENABLED \
    || env_var_enabled TRACKER_FINANCE_ROBINHOOD_AGENTIC_AUTO_TRADE_ENABLED \
    || env_var_enabled TRACKER_FINANCE_ROBINHOOD_AGENTIC_EXECUTION_ENABLED; then
  use_robinhood_agent=1
  echo "Including robinhood-agent service (Robinhood Agentic / auto-trade / execution enabled in ${env_file})."
fi
if env_var_enabled TRACKER_FINANCE_ROBINHOOD_AGENTIC_ENABLED && [[ "$use_robinhood_agent" -eq 0 ]]; then
  echo "WARN: TRACKER_FINANCE_ROBINHOOD_AGENTIC_ENABLED is set but robinhood-agent will not be started." >&2
  echo "      Remove TRACKER_ROBINHOOD_AGENTIC_DISABLE or touch .use-lightsail-robinhood-agent on the server." >&2
fi

use_robinhood_notebook=0
if [[ "${TRACKER_ROBINHOOD_NOTEBOOK:-0}" == "1" ]] || [[ -f "${repo_root}/.use-lightsail-robinhood-notebook" ]]; then
  use_robinhood_notebook=1
  echo "Including robinhood-notebook service (TRACKER_ROBINHOOD_NOTEBOOK=1 or .use-lightsail-robinhood-notebook)."
elif grep -qE '^[[:space:]]*TRACKER_ROBINHOOD_NOTEBOOK_DISABLE=1' "$env_file" 2>/dev/null; then
  echo "robinhood-notebook auto-start disabled (TRACKER_ROBINHOOD_NOTEBOOK_DISABLE=1 in ${env_file})."
elif grep -qE '^[[:space:]]*TRACKER_FINANCE_ROBINHOOD_NOTEBOOK_SERVICE_ENABLED=(true|1|yes)' "$env_file" 2>/dev/null; then
  use_robinhood_notebook=1
  echo "Including robinhood-notebook service (TRACKER_FINANCE_ROBINHOOD_NOTEBOOK_SERVICE_ENABLED in ${env_file})."
fi

build_services=( api web )
if [[ "$use_robinhood_agent" -eq 1 ]]; then
  build_services+=( robinhood-agent )
fi
if [[ "$use_robinhood_notebook" -eq 1 ]]; then
  build_services+=( robinhood-notebook )
fi

# --remove-orphans: dropping Caddy or Robinhood overlays no longer leaves old containers (e.g. tracker-pg-caddy-1).
# --force-recreate: avoids "container name already in use" when a prior run left a stale api/web container.
for svc in api web; do
  remove_stale_compose_containers "$svc"
done
docker compose "${compose_project[@]}" "${compose_files[@]}" --env-file "$env_file" build "${build_services[@]}"
if [[ "$use_robinhood_agent" -eq 1 ]]; then
  remove_stale_compose_containers robinhood-agent
  docker compose "${compose_project[@]}" "${compose_files[@]}" --env-file "$env_file" up -d --no-deps --force-recreate robinhood-agent
  if ! docker compose "${compose_project[@]}" "${compose_files[@]}" --env-file "$env_file" ps --status running --services 2>/dev/null | grep -qx robinhood-agent; then
    echo "ERROR: robinhood-agent failed to start. Check: docker compose ... logs robinhood-agent" >&2
    exit 1
  fi
  echo "robinhood-agent is running."
fi
docker compose "${compose_project[@]}" "${compose_files[@]}" --env-file "$env_file" up -d --no-deps --force-recreate --remove-orphans api web

wait_for_api_healthy() {
  local max_attempts="${1:-60}"
  local attempt=1
  echo "Waiting for api health (up to ${max_attempts} attempts)…"
  while (( attempt <= max_attempts )); do
    if docker compose "${compose_project[@]}" "${compose_files[@]}" --env-file "$env_file" exec -T api \
      curl -fsS http://127.0.0.1:9091/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
      echo "api is healthy."
      return 0
    fi
    sleep 2
    attempt=$((attempt + 1))
  done
  echo "WARN: api did not report healthy in time — UI may show 502 until Spring finishes booting." >&2
  echo "      Check: docker compose -f docker-compose.stack.yml --env-file ${env_file} logs api --tail 60" >&2
  return 1
}
wait_for_api_healthy 60 || true

# Ensure Caddy (re)starts and stays in the project; picks up Caddyfile bind-mount changes.
if [[ "$use_caddy" -eq 1 ]] && [[ -f "${repo_root}/docker-compose.https-lightsail.yml" ]]; then
  remove_stale_compose_containers caddy
  docker compose "${compose_project[@]}" "${compose_files[@]}" --env-file "$env_file" up -d caddy
fi
if [[ "$use_robinhood_notebook" -eq 1 ]]; then
  remove_stale_compose_containers robinhood-notebook
  docker compose "${compose_project[@]}" "${compose_files[@]}" --env-file "$env_file" up -d --no-deps --force-recreate robinhood-notebook
fi

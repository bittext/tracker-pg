#!/usr/bin/env bash
# Run on the Lightsail host from the repository root after `git pull` (see GitHub Actions deploy workflow).
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"
env_file="${TRACKER_ENV_FILE:-.env.stack}"
if [[ ! -f "$env_file" ]]; then
  echo "Missing ${env_file}. On the server: cp .env.stack.example .env.stack && edit secrets." >&2
  exit 1
fi
docker compose -f docker-compose.stack.yml --env-file "$env_file" up -d --build

#!/usr/bin/env bash
# Convenience wrapper: create or update login user "nisha" via UserUpsertSqlCli + PostgreSQL.
# Delegates to scripts/create-demo-user.sh; same .env.stack / Docker / psql behavior.
#
# Usage:
#   bash scripts/demo-user.sh
#       → same as: bash scripts/create-demo-user.sh nisha nisha123 USER false true -
#   bash scripts/demo-user.sh 'YourPassword'
#   bash scripts/demo-user.sh 'YourPassword' ADMIN
#   bash scripts/demo-user.sh 'YourPassword' USER false true -
#   bash scripts/demo-user.sh --help
#
# Any arguments you pass are appended after the fixed username "nisha" (password, role, mfa, active, phone).

set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  exec bash "${here}/create-demo-user.sh" --help
fi

if [[ "$#" -eq 0 ]]; then
  bash "${here}/create-demo-user.sh" nisha nisha123 USER false true -
  echo "" >&2
  echo "Sign in: username nisha  |  password nisha123" >&2
  exit 0
fi

exec bash "${here}/create-demo-user.sh" nisha "$@"

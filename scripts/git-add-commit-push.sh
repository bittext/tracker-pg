#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

commit_message="${1:-}"
if [[ -z "${commit_message}" ]]; then
  echo "Usage: $0 \"commit message\"" >&2
  exit 1
fi

git add -A

if git diff --cached --quiet; then
  echo "No staged changes to commit."
  exit 0
fi

git commit -m "${commit_message}"

current_branch="$(git rev-parse --abbrev-ref HEAD)"

if git rev-parse --abbrev-ref --symbolic-full-name "@{u}" >/dev/null 2>&1; then
  git push
else
  git push -u origin "${current_branch}"
fi

echo "Pushed ${current_branch} to origin."

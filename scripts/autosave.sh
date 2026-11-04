#!/bin/bash
# Momo Subtitle — checkpoint commit + push (AGENTS.md §2).
# Usage: bash scripts/autosave.sh [commit message]
set -u
cd "$(dirname "$0")/.." || exit 1

if [ -f .git/index.lock ]; then
  echo "autosave: git is busy (index.lock), skipping"
  exit 0
fi

git add -A
if git diff --cached --quiet; then
  echo "autosave: nothing to commit"
  exit 0
fi

MSG="${1:-chore(autosave): session checkpoint $(date '+%Y-%m-%d %H:%M' -u)}"
git commit -m "$MSG" -q
if git remote get-url origin >/dev/null 2>&1; then
  GIT_TERMINAL_PROMPT=0 git push -q origin HEAD && echo "autosave: committed and pushed" \
    || echo "autosave: committed locally but push failed (offline? auth?)"
else
  echo "autosave: committed locally (no origin configured yet)"
fi

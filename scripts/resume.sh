#!/bin/bash
# Momo Subtitle — session recovery briefing (AGENTS.md §2.5).
# Run this FIRST in any fresh session to reconstruct state.
set -u
cd "$(dirname "$0")/.." || exit 1

echo "=== git status ==="
git status -sb | head -20
echo
echo "=== last 8 commits ==="
git log --oneline -8 2>/dev/null || echo "(no commits yet)"
echo
echo "=== unpushed ==="
git log --oneline @{u}.. 2>/dev/null | head -10 || echo "(no upstream or everything pushed)"
echo
echo "=== worklog tail (last 30 lines) ==="
tail -30 worklog.md 2>/dev/null || echo "(worklog.md missing)"
echo
echo "=== next steps hint ==="
echo "1) Read the last worklog section above; continue its 'Next' item."
echo "2) Open GitHub issues (they are the authoritative task list)."
echo "3) If a build was interrupted: export ANDROID_HOME=/home/z/my-project/sdk; \\"
echo "   export GRADLE_USER_HOME=/home/z/my-project/.gradle-home; ./gradlew :app:assembleDebug"

#!/usr/bin/env bash
set -euo pipefail

APPLE_ID="${FASTLANE_USER:-igor.ganapolsky@icloud.com}"
REPO="${GITHUB_REPOSITORY:-IgorGanapolsky/AnswerGuard}"
OUT_FILE="${TMPDIR:-/tmp}/fastlane-spaceauth-output.txt"

if ! command -v fastlane >/dev/null 2>&1; then
  echo "fastlane is required" >&2
  exit 1
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "gh is required" >&2
  exit 1
fi

rm -f "$OUT_FILE"

set +e
fastlane spaceauth -u "$APPLE_ID" | tee "$OUT_FILE"
STATUS=${PIPESTATUS[0]}
set -e

if [[ "$STATUS" -ne 0 ]]; then
  echo "fastlane spaceauth failed" >&2
  exit "$STATUS"
fi

SESSION="$(
  python3 - "$OUT_FILE" <<'PY'
import re
import sys
from pathlib import Path

text = Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace")
patterns = [
    r"FASTLANE_SESSION=['\"]([^'\"]+)['\"]",
    r"export FASTLANE_SESSION=['\"]([^'\"]+)['\"]",
]
for pattern in patterns:
    match = re.search(pattern, text)
    if match:
        print(match.group(1))
        raise SystemExit(0)
raise SystemExit(1)
PY
)"

if [[ -z "$SESSION" ]]; then
  echo "Could not extract FASTLANE_SESSION from fastlane spaceauth output" >&2
  exit 1
fi

printf '%s' "$SESSION" | gh secret set FASTLANE_SESSION --repo "$REPO"
echo "Updated FASTLANE_SESSION for $REPO"

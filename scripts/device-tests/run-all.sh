#!/usr/bin/env bash
# run-all.sh — AnswerGuard device test orchestrator
# Runs Maestro flows against a connected Android device or emulator.
#
# Usage:
#   ./scripts/device-tests/run-all.sh [--skip-install]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

SKIP_INSTALL=false

RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
BOLD='\033[1m'
RESET='\033[0m'

# Parse arguments
for arg in "$@"; do
  case $arg in
    --skip-install) SKIP_INSTALL=true ;;
    --adb-only)
      echo "The legacy ADB shell suites were removed. Run the AnswerGuard Maestro flow instead."
      exit 2
      ;;
    --maestro-only) ;;
    --help|-h)
      echo "Usage: $0 [--skip-install] [--adb-only] [--maestro-only]"
      echo ""
      echo "  --skip-install  Skip APK build and install"
      echo "  --maestro-only  Accepted for compatibility; Maestro is the only suite"
      exit 0
      ;;
    *) echo "Unknown argument: $arg"; exit 2 ;;
  esac
done

echo -e "${BOLD}══════════════════════════════════════════${RESET}"
echo -e "${BOLD}  Device Tests — AnswerGuard${RESET}"
echo -e "${BOLD}══════════════════════════════════════════${RESET}"

# ── Phase 1: Preflight ──
echo -e "\n${CYAN}Phase 1: Preflight${RESET}"

# Check device connected
DEVICE_COUNT=$(adb devices | grep -c "device$" || true)
if [ "$DEVICE_COUNT" -eq 0 ]; then
  echo -e "${RED}No Android device/emulator connected.${RESET}"
  echo "Connect a device or start an emulator, then retry."
  exit 2
fi
echo -e "  ${GREEN}Device connected${RESET} ($DEVICE_COUNT device(s))"

# Install debug APK
if [ "$SKIP_INSTALL" = false ]; then
  echo -e "  Building and installing debug APK..."
  cd "$PROJECT_ROOT/native-android" && ./gradlew installDebug --no-daemon -q
  echo -e "  ${GREEN}APK installed${RESET}"
fi

# Grant runtime permissions
adb shell pm grant com.igorganapolsky.answerguard android.permission.POST_NOTIFICATIONS 2>/dev/null || true
echo -e "  ${GREEN}Permissions granted${RESET}"

# Start from a clean app process.
adb shell am force-stop com.igorganapolsky.answerguard 2>/dev/null || true
sleep 1

# ── Phase 2: Maestro Tests ──
MAESTRO_PASS=0
MAESTRO_FAIL=0

echo -e "\n${CYAN}Phase 2: Maestro Flows${RESET}"

if command -v maestro &>/dev/null; then
  MAESTRO_DIR="$PROJECT_ROOT/.maestro"
  MAESTRO_FLOWS=("smoke-test.yaml")

  for flow in "${MAESTRO_FLOWS[@]}"; do
    flow_path="$MAESTRO_DIR/$flow"
    echo -e "\n${BOLD}Running: $flow${RESET}"
    if maestro test "$flow_path"; then
      MAESTRO_PASS=$((MAESTRO_PASS + 1))
    else
      MAESTRO_FAIL=$((MAESTRO_FAIL + 1))
    fi
  done
else
  echo -e "  ${RED}Maestro CLI not found.${RESET}"
  exit 2
fi

# ── Phase 4: Summary ──
TOTAL_FAIL=$MAESTRO_FAIL

echo ""
echo -e "${BOLD}══════════════════════════════════════════${RESET}"
echo -e "${BOLD}  Summary${RESET}"
echo -e "${BOLD}──────────────────────────────────────────${RESET}"
echo -e "  Maestro flows:  ${GREEN}$MAESTRO_PASS passed${RESET}, ${RED}$MAESTRO_FAIL failed${RESET}"
echo -e "${BOLD}──────────────────────────────────────────${RESET}"

if [ "$TOTAL_FAIL" -gt 0 ]; then
  echo -e "  ${RED}RESULT: $TOTAL_FAIL test suite(s) failed${RESET}"
  echo -e "${BOLD}══════════════════════════════════════════${RESET}"
  exit 1
else
  echo -e "  ${GREEN}RESULT: ALL PASSED${RESET}"
  echo -e "${BOLD}══════════════════════════════════════════${RESET}"
  exit 0
fi

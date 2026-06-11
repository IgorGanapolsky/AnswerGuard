#!/usr/bin/env sh
# ci-connected-all.sh — Run every connectedDebugAndroidTest class on CI emulator.
# device-tests.yml runs Maestro smoke; native-release must gate on the full suite.
# One Gradle invocation per class + force-stop between classes avoids cross-class crashes on API 30.
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "${SCRIPT_DIR}/../.." && pwd)"

cd "${REPO_ROOT}/native-android"
chmod +x gradlew

# Stable order: onboarding first (fresh emulator), then activity screen.
TEST_CLASSES="
com.igorganapolsky.answerguard.screening.RoleOnboardingTest
com.igorganapolsky.answerguard.RecentActivityTest
"

echo "== Android connectedDebugAndroidTest (per-class isolation) =="
for test_class in ${TEST_CLASSES}; do
  adb shell am force-stop com.igorganapolsky.answerguard 2>/dev/null || true
  echo "-- class ${test_class} --"
  ./gradlew connectedDebugAndroidTest \
    --no-daemon \
    --stacktrace \
    -PenableFirebasePlugins=false \
    -Pandroid.testInstrumentationRunnerArguments.class="${test_class}"
done

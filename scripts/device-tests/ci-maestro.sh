#!/usr/bin/env sh
# ci-maestro.sh — Run all Android Maestro tests on an emulator in CI.
# Called by .github/workflows/device-tests.yml
set -eu

BASE_APP_ID="com.igorganapolsky.answerguard"
CI_APP_ID="${MAESTRO_APP_ID:-com.igorganapolsky.answerguard.debug}"
MAIN_ACTIVITY="com.igorganapolsky.answerguard.MainActivity"
MAESTRO_TMP_DIR="${TMPDIR:-/tmp}/answerguard-maestro-ci"

adb install -r native-android/app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant "$CI_APP_ID" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

# Re-enable animator duration for Compose rendering (Maestro needs it)
adb shell settings put global animator_duration_scale 1.0

rm -rf "$MAESTRO_TMP_DIR"
mkdir -p "$MAESTRO_TMP_DIR"
for maestro_file in .maestro/*.yaml; do
  sed \
    -e "s/appId: $BASE_APP_ID/appId: $CI_APP_ID/g" \
    -e "s/Package: $BASE_APP_ID/Package: $CI_APP_ID/g" \
    "$maestro_file" > "$MAESTRO_TMP_DIR/$(basename "$maestro_file")"
done

# Warm-start: launch app, wait for Compose to fully render, then kill.
# CI emulators are slow — Compose UI needs extra time on first launch.
adb shell am start -n "$CI_APP_ID/$MAIN_ACTIVITY"
sleep 15
adb shell am force-stop "$CI_APP_ID"
sleep 3

export PATH="$HOME/.maestro/bin:$PATH"
PASS=0
FAIL=0

# CI-safe subset: uses ci-smoke-test (no compact-mode dependency),
# excludes runScript tests and long-timeout alarm tests.
for flow in \
  ci-smoke-test.yaml \
  home-content-render.yaml \
  deep-link-open-home.yaml \
  pro-upgrade-cancel-returns-home.yaml \
  pro-upgrade-tap-shows-status.yaml \
  pro-restore-no-purchase.yaml
do
  flow_path="$MAESTRO_TMP_DIR/$flow"
  echo "== Running: $flow_path =="
  adb shell am force-stop "$CI_APP_ID" 2>/dev/null || true
  sleep 2
  if maestro test "$flow_path"; then
    echo "PASSED: $flow_path"
    PASS=$((PASS + 1))
  else
    echo "FAILED: $flow_path"
    FAIL=$((FAIL + 1))
    echo "=== DIAGNOSTICS FOR FAILED FLOW: $flow_path ==="
    echo "--- ADB DEVICES ---"
    adb devices
    echo "--- CURRENT ACTIVITY ---"
    adb shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' || true
    echo "--- DUMPING UI HIERARCHY ---"
    adb shell uiautomator dump /sdcard/window_dump.xml || true
    adb shell cat /sdcard/window_dump.xml || true
    echo "--- LOGCAT (LAST 150 LINES) ---"
    adb logcat -d | tail -n 150 || true
    echo "--- MAESTRO REPORT ---"
    latest_report=$(ls -td ~/.maestro/tests/* 2>/dev/null | head -n 1)
    if [ -n "$latest_report" ]; then
      echo "Latest Maestro report folder: $latest_report"
      ls -la "$latest_report" || true
      cat "$latest_report"/*.xml 2>/dev/null || true
    fi
    echo "=========================================="
  fi
done

echo "Results: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]

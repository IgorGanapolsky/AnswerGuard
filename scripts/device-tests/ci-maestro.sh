#!/usr/bin/env sh
# ci-maestro.sh — Run all Android Maestro tests on an emulator in CI.
# Called by .github/workflows/device-tests.yml
set -eu

adb install -r native-android/app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.igorganapolsky.answerguard android.permission.POST_NOTIFICATIONS 2>/dev/null || true

# Re-enable animator duration for Compose rendering (Maestro needs it)
adb shell settings put global animator_duration_scale 1.0

# Warm-start: launch app, wait for Compose to fully render, then kill.
# CI emulators are slow — Compose UI needs extra time on first launch.
adb shell am start -n com.igorganapolsky.answerguard/.MainActivity
sleep 15
adb shell am force-stop com.igorganapolsky.answerguard
sleep 3

export PATH="$HOME/.maestro/bin:$PATH"
PASS=0
FAIL=0

# CI-safe subset: uses ci-smoke-test (no compact-mode dependency),
# excludes runScript tests and long-timeout alarm tests.
for flow in \
  .maestro/ci-smoke-test.yaml \
  .maestro/home-content-render.yaml \
  .maestro/deep-link-open-home.yaml \
  .maestro/pro-upgrade-cancel-returns-home.yaml \
  .maestro/pro-upgrade-tap-shows-status.yaml \
  .maestro/pro-restore-no-purchase.yaml
do
  echo "== Running: $flow =="
  adb shell am force-stop com.igorganapolsky.answerguard 2>/dev/null || true
  sleep 2
  if maestro test "$flow"; then
    echo "PASSED: $flow"
    PASS=$((PASS + 1))
  else
    echo "FAILED: $flow"
    FAIL=$((FAIL + 1))
    echo "=== DIAGNOSTICS FOR FAILED FLOW: $flow ==="
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

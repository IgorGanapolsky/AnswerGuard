#!/usr/bin/env sh
# ci-maestro.sh — Run all Android Maestro tests on an emulator in CI.
# Called by .github/workflows/device-tests.yml
set -eu

BASE_APP_ID="com.igorganapolsky.answerguard"
CI_APP_ID="${MAESTRO_APP_ID:-com.igorganapolsky.answerguard.debug}"
MAIN_ACTIVITY="com.igorganapolsky.answerguard.MainActivity"
MAESTRO_TMP_DIR="${TMPDIR:-/tmp}/answerguard-maestro-ci"
MAESTRO_FLOW_TIMEOUT_SECONDS="${MAESTRO_FLOW_TIMEOUT_SECONDS:-180}"

# wait_for_boot — block until the emulator's adb is connected AND Android has
# fully finished booting (sys.boot_completed == 1). Times out at ~300s.
# This guards against the transient "Unable to connect to adb daemon" /
# half-booted-emulator infra flake that otherwise makes app launch fail.
wait_for_boot() {
  echo "== Waiting for device + adb readiness =="
  adb wait-for-device || true
  i=0
  while [ "$i" -lt 150 ]; do
    booted=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    if [ "$booted" = "1" ]; then
      echo "Boot completed (sys.boot_completed=1) after ${i} polls"
      # Dismiss the keyguard/lock screen so the app is interactable.
      adb shell input keyevent 82 2>/dev/null || true
      return 0
    fi
    i=$((i + 1))
    sleep 2
  done
  echo "WARNING: sys.boot_completed never reported 1 within timeout"
  return 1
}

# Ensure the emulator is actually booted before we touch it. The
# android-emulator-runner action waits for boot, but a flaky adb daemon can
# still leave us racing it — re-confirm explicitly.
wait_for_boot || true

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

  # First attempt.
  if timeout "$MAESTRO_FLOW_TIMEOUT_SECONDS" maestro test "$flow_path"; then
    echo "PASSED: $flow_path"
    PASS=$((PASS + 1))
    continue
  fi

  # First attempt failed. This is frequently a transient adb/launch flake
  # ("Unable to launch app ..."), not a real flow regression. Self-heal once:
  # restart the adb server, re-confirm the emulator is booted, then retry.
  # Crucially we do NOT swallow the result — the SECOND attempt's exit code is
  # what decides PASS/FAIL, so a genuinely broken flow still fails the job.
  echo "Attempt 1 failed for $flow_path — restarting adb and retrying once"
  adb kill-server || true
  adb start-server || true
  wait_for_boot || true
  adb shell am force-stop "$CI_APP_ID" 2>/dev/null || true
  sleep 3

  if timeout "$MAESTRO_FLOW_TIMEOUT_SECONDS" maestro test "$flow_path"; then
    echo "PASSED (on retry): $flow_path"
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

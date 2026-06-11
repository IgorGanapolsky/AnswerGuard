from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/device-tests/ci-connected-all.sh"


def test_ci_connected_all_runs_full_connected_debug_android_test_suite():
    source = SCRIPT.read_text(encoding="utf-8")

    assert "./gradlew connectedDebugAndroidTest" in source
    assert "testInstrumentationRunnerArguments.class" in source
    assert "am force-stop com.igorganapolsky.answerguard" in source
    assert "RoleOnboardingTest" in source
    assert "RecentActivityTest" in source
    assert "-PenableFirebasePlugins=false" in source
    assert source.count('for test_class in ${TEST_CLASSES}') == 1
    assert source.count("com.igorganapolsky.answerguard.") == 2


def test_ci_connected_all_lists_two_instrumentation_classes():
    source = SCRIPT.read_text(encoding="utf-8")
    for class_name in (
        "RoleOnboardingTest",
        "RecentActivityTest",
    ):
        assert class_name in source


def test_ci_connected_all_script_exists():
    assert SCRIPT.is_file()


def test_native_release_gates_android_device_tests_and_dynamic_version_code():
    native_release = (ROOT / ".github/workflows/native-release.yml").read_text(encoding="utf-8")
    assert "android-device-test-gate:" in native_release
    assert "needs.android-device-test-gate.result == 'success'" in native_release
    assert "compute_android_release_version_code.py" in native_release
    assert "-PciVersionCode=\"$VERSION_CODE\"" in native_release
    assert "bump-develop-version:" in native_release

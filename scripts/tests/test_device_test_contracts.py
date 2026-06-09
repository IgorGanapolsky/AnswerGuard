from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ANDROID_PACKAGE = "com.igorganapolsky.answerguard"
ANDROID_DEBUG_PACKAGE = "com.igorganapolsky.answerguard.debug"


def _read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def test_ci_maestro_flow_targets_answerguard_home_screen():
    source = _read(".maestro/ci-smoke-test.yaml")

    assert f"appId: {ANDROID_PACKAGE}" in source
    assert "AnswerGuard" in source
    assert "Call Screening" in source
    assert "open-settings-subscription.yaml" in source
    assert "Start Timer" not in source
    assert "randomtimer" not in source.lower()


def test_maestro_flows_do_not_reference_random_timer_app():
    for path in sorted((ROOT / ".maestro").glob("*.yaml")):
        if path.name == "dismiss-popups.yaml":
            continue
        source = path.read_text(encoding="utf-8")

        assert f"appId: {ANDROID_PACKAGE}" in source
        assert "AnswerGuard" in source
        assert "Random Tactical Timer" not in source
        assert "com.iganapolsky.randomtimer" not in source
        assert "com.igorganapolsky.randomtimer" not in source


def test_ci_maestro_runner_installs_and_controls_answerguard():
    source = _read("scripts/device-tests/ci-maestro.sh")

    assert "ci-smoke-test.yaml" in source
    assert ANDROID_PACKAGE in source
    assert ANDROID_DEBUG_PACKAGE in source
    assert "MAESTRO_APP_ID" in source
    assert "MAESTRO_FLOW_TIMEOUT_SECONDS" in source
    assert 'timeout "$MAESTRO_FLOW_TIMEOUT_SECONDS" maestro test "$flow_path"' in source
    assert "answerguard-maestro-ci" in source
    assert "com.iganapolsky.randomtimer" not in source
    assert "com.igorganapolsky.randomtimer" not in source

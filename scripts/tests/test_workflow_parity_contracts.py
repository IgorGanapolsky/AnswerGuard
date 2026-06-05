from __future__ import annotations

import re
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WORKFLOWS = ROOT / ".github" / "workflows"
FIREBASE_AGENT = ROOT / "scripts" / "ci_firebase_apptesting_execute.sh"
FIREBASE_CASE = ROOT / "firebase-apptesting" / "tests" / "answerguard-smoke.yaml"

EXPECTED_WORKFLOWS = {
    "asc-ground-truth.yml",
    "autonomous-release-automerge.yml",
    "firebase-app-testing-agent.yml",
    "ios-internal-retry.yml",
    "ios-reviews-ops.yml",
    "play-promote-to-production.yml",
    "store-release-watcher.yml",
}


def _workflow(name: str) -> str:
    return (WORKFLOWS / name).read_text(encoding="utf-8")


def test_reusable_random_timer_workflow_parity_files_exist():
    missing = [name for name in EXPECTED_WORKFLOWS if not (WORKFLOWS / name).is_file()]
    assert missing == []


def test_answer_guard_ids_are_used_in_store_and_play_workflows():
    combined = "\n".join(_workflow(name) for name in EXPECTED_WORKFLOWS)

    assert "com.igorganapolsky.answerguard" in combined
    assert "AnswerGuard" in combined
    assert "randomtimer" not in combined.lower()
    assert "random tactical timer" not in combined.lower()
    assert "com.iganapolsky" not in combined


def test_firebase_app_testing_agent_has_answer_guard_case_and_help():
    assert FIREBASE_AGENT.is_file()
    assert FIREBASE_CASE.is_file()

    case = FIREBASE_CASE.read_text(encoding="utf-8")
    assert "AnswerGuard" in case
    assert "Call Screening" in case
    assert "Start Timer" not in case
    assert "Random Tactical Timer" not in case

    result = subprocess.run(
        ["bash", str(FIREBASE_AGENT), "--help"],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )
    assert result.returncode == 0
    assert "--test-devices" in result.stdout
    assert "FIREBASE_ANDROID_APP_ID" in result.stdout


def test_workflow_dispatch_surfaces_are_guarded():
    play = _workflow("play-promote-to-production.yml")
    automerge = _workflow("autonomous-release-automerge.yml")
    watcher = _workflow("store-release-watcher.yml")

    assert re.search(r"version_code:\n\s+description:", play)
    assert "environment: production" in play
    assert "base.ref == 'main'" in automerge
    assert "startsWith(github.event.pull_request.head.ref, 'release/')" in automerge
    assert "python scripts/source_versions.py --format value --key IOS_VERSION_NAME" in watcher


def test_app_privacy_publish_workflows_allow_fastlane_session_auth():
    for workflow_name in ["ios-app-privacy-publish.yml", "ios-submit-review.yml"]:
        workflow = _workflow(workflow_name)
        assert "FASTLANE_SESSION: ${{ secrets.FASTLANE_SESSION }}" in workflow
        assert "SPACESHIP_SESSION: ${{ secrets.FASTLANE_SESSION }}" in workflow
        assert "FASTLANE_PASSWORD: session-auth-placeholder" in workflow
        assert "Session loaded from environment variable is not valid" in workflow
        assert "Apple rejected the FASTLANE_SESSION on this GitHub runner" in workflow

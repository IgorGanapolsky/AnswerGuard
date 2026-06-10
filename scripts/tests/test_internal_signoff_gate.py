from __future__ import annotations

import json

import pytest

from scripts import internal_signoff_gate as gate


def _payload(*statuses: dict[str, str]) -> dict[str, list[dict[str, str]]]:
    return {"statuses": list(statuses)}


def test_both_requires_both_statuses():
    passed, problems = gate.evaluate_signoff(
        "both",
        _payload(
            {"context": "internal-signoff/testflight", "state": "success"},
            {"context": "internal-signoff/firebase", "state": "success"},
        ),
    )
    assert passed is True
    assert problems == []


def test_missing_required_status_fails():
    passed, problems = gate.evaluate_signoff(
        "both",
        _payload({"context": "internal-signoff/testflight", "state": "success"}),
    )
    assert passed is False
    assert problems == ["missing required status: internal-signoff/firebase"]


def test_main_returns_zero_when_signoff_present(tmp_path, monkeypatch):
    payload_path = tmp_path / "statuses.json"
    payload_path.write_text(
        json.dumps(_payload({"context": "internal-signoff/firebase", "state": "success"})),
        encoding="utf-8",
    )
    monkeypatch.setattr(
        "sys.argv",
        ["internal_signoff_gate.py", "--platform", "android", "--statuses-json", str(payload_path)],
    )
    assert gate.main() == 0

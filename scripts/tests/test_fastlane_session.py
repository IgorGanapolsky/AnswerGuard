from __future__ import annotations

import pytest

from scripts.fastlane_session import FastlaneSessionError, extract_fastlane_session


COOKIE_PREFIX = "---\n- !ruby/object:HTTP::Cookie"


def test_extract_fastlane_session_decodes_spaceauth_export():
    output = "\n".join(
        [
            "Pass the following via FASTLANE_SESSION:",
            "export FASTLANE_SESSION='---\\n- !ruby/object:HTTP::Cookie\\n  name: myacinfo'",
        ]
    )

    assert extract_fastlane_session(output) == f"{COOKIE_PREFIX}\n  name: myacinfo"


def test_extract_fastlane_session_prefers_export_line():
    output = "\n".join(
        [
            "FASTLANE_SESSION='not a cookie'",
            "export FASTLANE_SESSION='---\\n- !ruby/object:HTTP::Cookie\\n  name: session'",
        ]
    )

    assert extract_fastlane_session(output) == f"{COOKIE_PREFIX}\n  name: session"


def test_extract_fastlane_session_rejects_missing_or_invalid_values():
    with pytest.raises(FastlaneSessionError, match="Could not find"):
        extract_fastlane_session("no session here")

    with pytest.raises(FastlaneSessionError, match="does not look"):
        extract_fastlane_session("export FASTLANE_SESSION='not a cookie'")

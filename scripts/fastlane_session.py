from __future__ import annotations

import ast
import re


class FastlaneSessionError(ValueError):
    pass


def _decode_shell_quoted(value: str) -> str:
    quoted = "'" + value.replace("'", "\\'") + "'"
    try:
        return ast.literal_eval(quoted)
    except (SyntaxError, ValueError) as exc:
        raise FastlaneSessionError("Could not decode FASTLANE_SESSION value") from exc


def extract_fastlane_session(text: str) -> str:
    patterns = [
        r"(?:^|\n)\s*export\s+FASTLANE_SESSION=(['\"])(.*?)\1",
        r"(?:^|\n)\s*FASTLANE_SESSION=(['\"])(.*?)\1",
    ]

    for pattern in patterns:
        match = re.search(pattern, text, re.DOTALL)
        if not match:
            continue
        session = _decode_shell_quoted(match.group(2))
        if not session.startswith("---\n- !ruby/object:HTTP::Cookie"):
            raise FastlaneSessionError("Extracted FASTLANE_SESSION does not look like a fastlane cookie jar")
        return session

    raise FastlaneSessionError("Could not find FASTLANE_SESSION in fastlane spaceauth output")

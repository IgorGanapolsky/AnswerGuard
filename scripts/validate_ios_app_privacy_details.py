#!/usr/bin/env python3
"""Validate fastlane App Privacy Details JSON before publishing to ASC."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


VALID_CATEGORIES = {
    "PAYMENT_INFORMATION",
    "CREDIT_AND_FRAUD",
    "OTHER_FINANCIAL_INFO",
    "PRECISE_LOCATION",
    "SENSITIVE_INFO",
    "PHYSICAL_ADDRESS",
    "EMAIL_ADDRESS",
    "NAME",
    "PHONE_NUMBER",
    "OTHER_CONTACT_INFO",
    "CONTACTS",
    "EMAILS_OR_TEXT_MESSAGES",
    "PHOTOS_OR_VIDEOS",
    "AUDIO",
    "GAMEPLAY_CONTENT",
    "CUSTOMER_SUPPORT",
    "OTHER_USER_CONTENT",
    "BROWSING_HISTORY",
    "SEARCH_HISTORY",
    "USER_ID",
    "DEVICE_ID",
    "PURCHASE_HISTORY",
    "PRODUCT_INTERACTION",
    "ADVERTISING_DATA",
    "OTHER_USAGE_DATA",
    "CRASH_DATA",
    "PERFORMANCE_DATA",
    "OTHER_DIAGNOSTIC_DATA",
    "OTHER_DATA",
    "HEALTH",
    "FITNESS",
    "COARSE_LOCATION",
}

VALID_PURPOSES = {
    "THIRD_PARTY_ADVERTISING",
    "DEVELOPERS_ADVERTISING",
    "ANALYTICS",
    "PRODUCT_PERSONALIZATION",
    "APP_FUNCTIONALITY",
    "OTHER_PURPOSES",
}

VALID_DATA_PROTECTIONS = {
    "DATA_USED_TO_TRACK_YOU",
    "DATA_LINKED_TO_YOU",
    "DATA_NOT_LINKED_TO_YOU",
    "DATA_NOT_COLLECTED",
}


def _die(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def _require_string_list(value: Any, *, field: str, index: int) -> list[str]:
    if not isinstance(value, list) or not all(isinstance(item, str) for item in value):
        _die(f"entry {index}: {field} must be a list of strings")
    return value


def validate(payload: Any) -> None:
    if not isinstance(payload, list) or not payload:
        _die("payload must be a non-empty list")

    seen_categories: set[str] = set()
    for index, entry in enumerate(payload):
        if not isinstance(entry, dict):
            _die(f"entry {index}: must be an object")

        protections = _require_string_list(
            entry.get("data_protections"),
            field="data_protections",
            index=index,
        )

        if protections == ["DATA_NOT_COLLECTED"]:
            if "category" in entry or "purposes" in entry:
                _die("DATA_NOT_COLLECTED entry must not include category or purposes")
            if len(payload) != 1:
                _die("DATA_NOT_COLLECTED must be the only entry")
            continue

        category = entry.get("category")
        if not isinstance(category, str):
            _die(f"entry {index}: category is required")
        if category not in VALID_CATEGORIES:
            _die(f"entry {index}: unknown category {category!r}")
        if category in seen_categories:
            _die(f"entry {index}: duplicate category {category!r}")
        seen_categories.add(category)

        purposes = _require_string_list(entry.get("purposes"), field="purposes", index=index)
        if not purposes:
            _die(f"entry {index}: purposes must not be empty")

        unknown_purposes = sorted(set(purposes) - VALID_PURPOSES)
        if unknown_purposes:
            _die(f"entry {index}: unknown purposes {unknown_purposes}")

        unknown_protections = sorted(set(protections) - VALID_DATA_PROTECTIONS)
        if unknown_protections:
            _die(f"entry {index}: unknown data protections {unknown_protections}")

        if "DATA_NOT_COLLECTED" in protections:
            _die(f"entry {index}: DATA_NOT_COLLECTED cannot be combined with a category")

        if "DATA_LINKED_TO_YOU" in protections and "DATA_NOT_LINKED_TO_YOU" in protections:
            _die(f"entry {index}: data cannot be both linked and not linked to the user")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "path",
        nargs="?",
        default="native-ios/fastlane/app_privacy_details.json",
        help="Path to fastlane app_privacy_details.json",
    )
    args = parser.parse_args()

    path = Path(args.path)
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        _die(f"file not found: {path}")
    except json.JSONDecodeError as exc:
        _die(f"invalid JSON: {exc}")

    validate(payload)
    print(f"OK: {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

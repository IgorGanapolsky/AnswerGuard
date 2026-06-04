from __future__ import annotations

import pytest

from scripts.validate_ios_app_privacy_details import validate


def test_accepts_repo_privacy_payload_shape():
    validate(
        [
            {
                "category": "CRASH_DATA",
                "purposes": ["APP_FUNCTIONALITY", "ANALYTICS"],
                "data_protections": ["DATA_NOT_LINKED_TO_YOU"],
            }
        ]
    )


def test_accepts_not_collecting_data_payload():
    validate([{"data_protections": ["DATA_NOT_COLLECTED"]}])


def test_rejects_unknown_category():
    with pytest.raises(SystemExit):
        validate(
            [
                {
                    "category": "BOGUS",
                    "purposes": ["ANALYTICS"],
                    "data_protections": ["DATA_NOT_LINKED_TO_YOU"],
                }
            ]
        )


def test_rejects_conflicting_linkage_protections():
    with pytest.raises(SystemExit):
        validate(
            [
                {
                    "category": "CRASH_DATA",
                    "purposes": ["ANALYTICS"],
                    "data_protections": [
                        "DATA_LINKED_TO_YOU",
                        "DATA_NOT_LINKED_TO_YOU",
                    ],
                }
            ]
        )

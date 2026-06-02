#!/usr/bin/env python3
"""Ensure iOS bundle IDs have the App Groups capability configured.

This runs before fastlane match regenerates App Store provisioning profiles.
If the Apple Developer Portal bundle ID lacks App Groups, match can create a
profile that does not contain com.apple.security.application-groups, and Xcode
then rejects the archive even though the repo entitlements are correct.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Any

_SCRIPTS_DIR = str(Path(__file__).resolve().parent)
if _SCRIPTS_DIR not in sys.path:
    sys.path.insert(0, _SCRIPTS_DIR)

from asc_client import ASCClient, AscClientError


DEFAULT_BUNDLE_IDS = [
    "com.igorganapolsky.answerguard",
    "com.igorganapolsky.answerguard.widget",
    "com.igorganapolsky.answerguard.calldirectory",
]
DEFAULT_APP_GROUP = "group.com.igorganapolsky.answerguard"


def _app_group_settings(app_group: str) -> list[dict[str, Any]]:
    return [
        {
            "key": "APP_GROUP_IDS",
            "options": [{"key": app_group, "enabled": True}],
        }
    ]


class IosAppGroupEnsurer:
    def __init__(self, client: ASCClient):
        self.client = client

    def _bundle_id_resource(self, bundle_identifier: str) -> dict[str, Any]:
        rows = self.client.get_all(
            "/bundleIds",
            params={
                "filter[identifier]": bundle_identifier,
                "fields[bundleIds]": "identifier,name,platform",
                "limit": "200",
            },
        )
        for row in rows:
            if row.get("attributes", {}).get("identifier") == bundle_identifier:
                return row
        raise RuntimeError(f"Bundle ID not found in App Store Connect: {bundle_identifier}")

    def _app_groups_capability(self, bundle_id_resource_id: str) -> dict[str, Any] | None:
        capabilities = self.client.get_all(
            f"/bundleIds/{bundle_id_resource_id}/bundleIdCapabilities",
            params={
                "fields[bundleIdCapabilities]": "capabilityType,settings",
                "limit": "200",
            },
        )
        for capability in capabilities:
            if capability.get("attributes", {}).get("capabilityType") == "APP_GROUPS":
                return capability
        return None

    def ensure(self, bundle_identifier: str, app_group: str) -> str:
        bundle = self._bundle_id_resource(bundle_identifier)
        bundle_id_resource_id = bundle["id"]
        capability = self._app_groups_capability(bundle_id_resource_id)
        settings = _app_group_settings(app_group)

        if capability is None:
            self.client.request(
                "POST",
                "/bundleIdCapabilities",
                payload={
                    "data": {
                        "type": "bundleIdCapabilities",
                        "attributes": {
                            "capabilityType": "APP_GROUPS",
                            "settings": settings,
                        },
                        "relationships": {
                            "bundleId": {
                                "data": {"type": "bundleIds", "id": bundle_id_resource_id}
                            }
                        },
                    }
                },
            )
            return "created"

        cap_id = capability["id"]
        current_settings = capability.get("attributes", {}).get("settings") or []
        if current_settings == settings:
            return "unchanged"

        self.client.request(
            "PATCH",
            f"/bundleIdCapabilities/{cap_id}",
            payload={
                "data": {
                    "type": "bundleIdCapabilities",
                    "id": cap_id,
                    "attributes": {"settings": settings},
                }
            },
        )
        return "updated"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--app-group", default=DEFAULT_APP_GROUP)
    parser.add_argument("--bundle-id", action="append", dest="bundle_ids")
    args = parser.parse_args()

    bundle_ids = args.bundle_ids or DEFAULT_BUNDLE_IDS

    try:
        ensurer = IosAppGroupEnsurer(ASCClient.from_env())
        for bundle_id in bundle_ids:
            result = ensurer.ensure(bundle_id, args.app_group)
            print(f"{result}: {bundle_id} -> {args.app_group}")
    except (AscClientError, RuntimeError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

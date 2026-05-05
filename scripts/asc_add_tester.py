#!/usr/bin/env python3
"""Invite a TestFlight beta tester and attach the latest build to a group."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Any, NamedTuple

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from asc_client import ASCClient, AscClientError

DEFAULT_BUNDLE_ID = "com.igorganapolsky.answerguard"
DEFAULT_GROUP = "Internal Testers"


class BetaGroup(NamedTuple):
    id: str
    is_internal: bool


def _attrs(item: dict[str, Any]) -> dict[str, Any]:
    return item.get("attributes", {}) or {}


def find_app_id(client: ASCClient, bundle_id: str) -> str:
    apps = client.get("/apps", params={"filter[bundleId]": bundle_id, "limit": "1"}).get("data", [])
    if not apps:
        raise RuntimeError(f"No App Store Connect app found for bundle id '{bundle_id}'")
    return apps[0]["id"]


def find_or_create_group(client: ASCClient, app_id: str, group_name: str) -> BetaGroup:
    groups = client.get_all(f"/apps/{app_id}/betaGroups", params={"limit": "200"})
    for group in groups:
        attrs = _attrs(group)
        if attrs.get("name") == group_name:
            return BetaGroup(group["id"], bool(attrs.get("isInternalGroup", False)))

    payload = {
        "data": {
            "type": "betaGroups",
            "attributes": {"name": group_name, "isInternalGroup": False},
            "relationships": {"app": {"data": {"type": "apps", "id": app_id}}},
        }
    }
    created = client.request("POST", "/betaGroups", payload=payload)
    return BetaGroup(created["data"]["id"], False)


def find_or_create_tester(
    client: ASCClient,
    *,
    email: str,
    first_name: str,
    last_name: str,
    group_id: str,
) -> str:
    testers = client.get_all("/betaTesters", params={"filter[email]": email, "limit": "200"})
    if testers:
        tester_id = testers[0]["id"]
    else:
        payload = {
            "data": {
                "type": "betaTesters",
                "attributes": {
                    "email": email,
                    "firstName": first_name,
                    "lastName": last_name,
                },
                "relationships": {
                    "betaGroups": {"data": [{"type": "betaGroups", "id": group_id}]}
                },
            }
        }
        tester_id = client.request("POST", "/betaTesters", payload=payload)["data"]["id"]

    try:
        client.request(
            "POST",
            f"/betaGroups/{group_id}/relationships/betaTesters",
            payload={"data": [{"type": "betaTesters", "id": tester_id}]},
        )
    except AscClientError as exc:
        if "HTTP 409" not in str(exc):
            raise
    return tester_id


def distribute_latest_build(
    client: ASCClient, *, app_id: str, group_id: str, is_internal_group: bool = False
) -> str | None:
    builds = client.get_all(
        "/builds",
        params={
            "filter[app]": app_id,
            "sort": "-uploadedDate",
            "limit": "50",
            "fields[builds]": "version,uploadedDate,processingState",
        },
    )
    if not builds:
        return None

    latest = builds[0]
    build_id = latest["id"]
    if is_internal_group:
        return str(_attrs(latest).get("version") or build_id)

    try:
        client.request(
            "POST",
            f"/betaGroups/{group_id}/relationships/builds",
            payload={"data": [{"type": "builds", "id": build_id}]},
        )
    except AscClientError as exc:
        if "HTTP 409" not in str(exc):
            raise
    return str(_attrs(latest).get("version") or build_id)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--email", required=True)
    parser.add_argument("--first-name", default="")
    parser.add_argument("--last-name", default="")
    parser.add_argument("--group", default=DEFAULT_GROUP)
    parser.add_argument("--bundle-id", default=DEFAULT_BUNDLE_ID)
    args = parser.parse_args()

    client = ASCClient.from_env()
    app_id = find_app_id(client, args.bundle_id)
    group = find_or_create_group(client, app_id, args.group)
    find_or_create_tester(
        client,
        email=args.email,
        first_name=args.first_name,
        last_name=args.last_name,
        group_id=group.id,
    )
    build = distribute_latest_build(client, app_id=app_id, group_id=group.id, is_internal_group=group.is_internal)
    if build:
        if group.is_internal:
            print(f"✅ {args.email} is in '{args.group}'. Latest internal build is {build}.")
        else:
            print(f"✅ {args.email} is in '{args.group}' and build {build} is attached.")
    else:
        print(f"✅ {args.email} is in '{args.group}'. No TestFlight builds are available yet.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

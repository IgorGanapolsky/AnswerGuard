#!/usr/bin/env python3
"""Create or update App Store Review contact details for an App Store version."""

from __future__ import annotations

import argparse
import os
from typing import Any

try:
    from scripts.asc_client import ASCClient, AscClientError
except Exception:
    from asc_client import ASCClient, AscClientError


DEFAULT_BUNDLE_ID = "com.igorganapolsky.answerguard"


def die(message: str, code: int = 1) -> None:
    print(f"❌ {message}")
    raise SystemExit(code)


def _env_required(name: str) -> str:
    value = (os.environ.get(name) or "").strip()
    if not value:
        die(f"Missing required env var: {name}", code=2)
    return value


def _env_bool(name: str, default: bool = False) -> bool:
    value = (os.environ.get(name) or "").strip().lower()
    if not value:
        return default
    return value in {"1", "true", "yes", "y"}


def contact_attributes_from_env() -> dict[str, Any]:
    attributes: dict[str, Any] = {
        "contactFirstName": _env_required("APP_REVIEW_CONTACT_FIRST_NAME"),
        "contactLastName": _env_required("APP_REVIEW_CONTACT_LAST_NAME"),
        "contactEmail": _env_required("APP_REVIEW_CONTACT_EMAIL"),
        "contactPhone": _env_required("APP_REVIEW_CONTACT_PHONE"),
        "demoAccountRequired": _env_bool("APP_REVIEW_DEMO_ACCOUNT_REQUIRED", False),
    }
    optional_map = {
        "APP_REVIEW_DEMO_ACCOUNT_NAME": "demoAccountName",
        "APP_REVIEW_DEMO_ACCOUNT_PASSWORD": "demoAccountPassword",
        "APP_REVIEW_NOTES": "notes",
    }
    for env_name, attr_name in optional_map.items():
        value = (os.environ.get(env_name) or "").strip()
        if value:
            attributes[attr_name] = value
    return attributes


def get_app_id(client: ASCClient, bundle_id: str) -> str:
    apps = client.get_all("/apps", params={"filter[bundleId]": bundle_id, "limit": 1})
    if not apps:
        die(f"No App Store Connect app found for bundleId={bundle_id}", code=2)
    return str(apps[0]["id"])


def find_app_store_version_id(client: ASCClient, app_id: str, version: str) -> str:
    versions = client.get_all(f"/apps/{app_id}/appStoreVersions", params={"limit": 50})
    for item in versions:
        attrs = item.get("attributes") or {}
        if attrs.get("versionString") == version:
            return str(item["id"])
    die(f"App Store version {version} not found for app id {app_id}", code=2)
    raise AssertionError("unreachable")


def get_review_detail(client: ASCClient, version_id: str) -> dict[str, Any] | None:
    try:
        data = client.get(f"/appStoreVersions/{version_id}/appStoreReviewDetail")
    except AscClientError:
        return None
    detail = data.get("data")
    return detail if isinstance(detail, dict) and detail.get("id") else None


def create_review_detail(client: ASCClient, version_id: str, attributes: dict[str, Any]) -> dict[str, Any]:
    payload = {
        "data": {
            "type": "appStoreReviewDetails",
            "attributes": attributes,
            "relationships": {
                "appStoreVersion": {
                    "data": {"type": "appStoreVersions", "id": version_id},
                },
            },
        },
    }
    return client.request("POST", "/appStoreReviewDetails", payload=payload)


def patch_review_detail(
    client: ASCClient,
    detail_id: str,
    existing_attributes: dict[str, Any],
    desired_attributes: dict[str, Any],
) -> dict[str, Any] | None:
    changed = {
        key: value
        for key, value in desired_attributes.items()
        if existing_attributes.get(key) != value
    }
    if not changed:
        return None
    payload = {
        "data": {
            "id": detail_id,
            "type": "appStoreReviewDetails",
            "attributes": changed,
        },
    }
    return client.request("PATCH", f"/appStoreReviewDetails/{detail_id}", payload=payload)


def ensure_review_detail(
    client: ASCClient,
    *,
    bundle_id: str,
    version: str,
    attributes: dict[str, Any],
) -> dict[str, Any]:
    app_id = get_app_id(client, bundle_id)
    version_id = find_app_store_version_id(client, app_id, version)
    existing = get_review_detail(client, version_id)
    if existing is None:
        created = create_review_detail(client, version_id, attributes)
        return {"status": "created", "version_id": version_id, "detail_id": (created.get("data") or {}).get("id")}

    detail_id = str(existing["id"])
    existing_attrs = (existing.get("attributes") or {}) if isinstance(existing, dict) else {}
    patched = patch_review_detail(client, detail_id, existing_attrs, attributes)
    if patched is None:
        return {"status": "unchanged", "version_id": version_id, "detail_id": detail_id}
    return {"status": "updated", "version_id": version_id, "detail_id": detail_id}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Ensure App Store Review contact details exist.")
    parser.add_argument("--bundle-id", default=DEFAULT_BUNDLE_ID)
    parser.add_argument("--version", required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        client = ASCClient.from_env(timeout=30)
        result = ensure_review_detail(
            client,
            bundle_id=args.bundle_id,
            version=args.version,
            attributes=contact_attributes_from_env(),
        )
    except AscClientError as exc:
        die(str(exc), code=2)
    print(
        "✅ App Review detail "
        f"{result['status']} for App Store version id={result['version_id']} "
        f"(detail id={result.get('detail_id') or 'unknown'})"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Ensure the App Groups capability + app-group association on AnswerGuard's App IDs.

Context
-------
The iOS release build fails with:

    Provisioning profile doesn't include the App Groups capability

`match`-regenerated provisioning profiles only carry the App Groups entitlement if,
on the Apple Developer portal, each App ID:

  1. has the **App Groups** capability (capabilityType ``APP_GROUPS``) enabled, AND
  2. has the concrete app group ``group.com.igorganapolsky.answerguard`` associated
     with that capability.

This script drives the **App Store Connect REST API** (api.appstoreconnect.apple.com)
with API-key (ES256 JWT) auth — we have NO Apple-ID password, so the cookie-auth
developer-portal ("spaceship") API is not an option.

What is and isn't possible via the public ASC API (verified empirically by this
script and printed as evidence):

  * ``GET  /v1/bundleIds?filter[identifier]=<id>``      -> supported (resolve bundle id)
  * ``GET  /v1/bundleIds/{id}/bundleIdCapabilities``     -> supported (read capabilities)
  * ``POST /v1/bundleIdCapabilities`` (APP_GROUPS)       -> supported (enable capability)
  * App Group *resources* (``/v1/appGroups``) + associating a specific app group with
    a bundle id                                          -> historically NOT in the
    public key-auth API (only the cookie-auth portal API / Developer Portal UI).

So this script does as much as the public API genuinely allows (enable the
APP_GROUPS capability on each App ID) and then *probes* the app-group resource
endpoints, capturing the EXACT HTTP status + response body as evidence — so we can
definitively conclude whether the create/associate step must be done manually in the
portal.

It is idempotent and safe to re-run. It exits non-zero only on a hard failure
(e.g. cannot authenticate, or a bundle id cannot be resolved at all), but ALWAYS
prints what worked and what did not.
"""

from __future__ import annotations

import json
import os
import sys
import time
from typing import Any, Dict, List, Optional, Tuple

APP_STORE_CONNECT_API = "https://api.appstoreconnect.apple.com/v1"

BUNDLE_IDS: List[str] = [
    "com.igorganapolsky.answerguard",
    "com.igorganapolsky.answerguard.widget",
    "com.igorganapolsky.answerguard.calldirectory",
]

APP_GROUP_IDENTIFIER = "group.com.igorganapolsky.answerguard"
APP_GROUP_NAME = "AnswerGuard App Group"
CAPABILITY_TYPE = "APP_GROUPS"


# --------------------------------------------------------------------------- #
# Auth (ES256 JWT) — mirrors scripts/check_store_access.py
# --------------------------------------------------------------------------- #
def _read_appstore_key_material(key_id: str) -> str:
    private_key = (os.environ.get("APPSTORE_PRIVATE_KEY") or "").strip()
    if not private_key:
        private_key = (os.environ.get("APPSTORE_PRIVATE_KEY_PATH") or "").strip()
    if not private_key:
        default_path = os.path.expanduser(
            f"~/.appstoreconnect/private_keys/AuthKey_{key_id}.p8"
        )
        if os.path.isfile(default_path):
            private_key = default_path
    if not private_key:
        return ""
    expanded_path = os.path.expanduser(private_key)
    if os.path.isfile(expanded_path):
        with open(expanded_path, "r", encoding="utf-8") as f:
            return f.read()
    return private_key


def build_asc_jwt() -> Tuple[Optional[str], str]:
    try:
        import jwt  # PyJWT
    except ImportError:
        return (None, "Missing dependencies. Install: pip install pyjwt cryptography")

    key_id = (os.environ.get("APPSTORE_KEY_ID") or "").strip()
    issuer_id = (os.environ.get("APPSTORE_ISSUER_ID") or "").strip()
    private_key = _read_appstore_key_material(key_id)

    missing: List[str] = []
    if not key_id:
        missing.append("APPSTORE_KEY_ID")
    if not issuer_id:
        missing.append("APPSTORE_ISSUER_ID")
    if not private_key:
        missing.append("APPSTORE_PRIVATE_KEY (or APPSTORE_PRIVATE_KEY_PATH)")
    if missing:
        return (None, f"Missing App Store credentials: {', '.join(missing)}")

    now = int(time.time())
    payload = {
        "iss": issuer_id,
        "iat": now,
        "exp": now + 1200,
        "aud": "appstoreconnect-v1",
    }
    headers = {"alg": "ES256", "kid": key_id, "typ": "JWT"}
    token = jwt.encode(payload, private_key, algorithm="ES256", headers=headers)
    return (token, "")


# --------------------------------------------------------------------------- #
# Thin HTTP layer that surfaces the raw status + body for evidence
# --------------------------------------------------------------------------- #
class ApiResult:
    def __init__(self, status: int, body: Any, ok: bool):
        self.status = status
        self.body = body
        self.ok = ok

    def body_text(self, limit: int = 2000) -> str:
        try:
            return json.dumps(self.body, indent=2, sort_keys=True)[:limit]
        except Exception:
            return str(self.body)[:limit]


class AscApi:
    def __init__(self, token: str, timeout: int = 30):
        import requests  # imported lazily so --help / py_compile work without it

        self._requests = requests
        self._token = token
        self._timeout = timeout

    def request(
        self,
        method: str,
        path: str,
        *,
        params: Optional[Dict[str, Any]] = None,
        payload: Optional[Dict[str, Any]] = None,
    ) -> ApiResult:
        url = path if path.startswith("https://") else f"{APP_STORE_CONNECT_API}{path}"
        resp = self._requests.request(
            method.upper(),
            url,
            headers={
                "Authorization": f"Bearer {self._token}",
                "Content-Type": "application/json",
            },
            params=params or {},
            json=payload,
            timeout=self._timeout,
        )
        if resp.content:
            try:
                body: Any = resp.json()
            except Exception:
                body = {"raw": (resp.text or "")[:2000]}
        else:
            body = {}
        return ApiResult(resp.status_code, body, 200 <= resp.status_code < 300)


# --------------------------------------------------------------------------- #
# Bundle-id capability operations
# --------------------------------------------------------------------------- #
def resolve_bundle_id(api: AscApi, identifier: str) -> Tuple[Optional[str], ApiResult]:
    res = api.request(
        "GET",
        "/bundleIds",
        params={"filter[identifier]": identifier, "limit": 200},
    )
    if not res.ok:
        return (None, res)
    # filter[identifier] is a prefix-ish match on some accounts; pick the exact one.
    for item in res.body.get("data", []) or []:
        if (item.get("attributes", {}) or {}).get("identifier") == identifier:
            return (item.get("id"), res)
    data = res.body.get("data", []) or []
    if len(data) == 1:
        return (data[0].get("id"), res)
    return (None, res)


def read_capabilities(api: AscApi, bundle_resource_id: str) -> Tuple[List[str], ApiResult]:
    res = api.request(
        "GET",
        f"/bundleIds/{bundle_resource_id}/bundleIdCapabilities",
        params={"limit": 200},
    )
    if not res.ok:
        return ([], res)
    types = [
        (item.get("attributes", {}) or {}).get("capabilityType")
        for item in (res.body.get("data", []) or [])
    ]
    return ([t for t in types if t], res)


def enable_app_groups_capability(api: AscApi, bundle_resource_id: str) -> ApiResult:
    payload = {
        "data": {
            "type": "bundleIdCapabilities",
            "attributes": {"capabilityType": CAPABILITY_TYPE},
            "relationships": {
                "bundleId": {
                    "data": {"type": "bundleIds", "id": bundle_resource_id}
                }
            },
        }
    }
    return api.request("POST", "/bundleIdCapabilities", payload=payload)


# --------------------------------------------------------------------------- #
# App Group resource probes (capture exact evidence)
# --------------------------------------------------------------------------- #
def probe_app_group_resource(api: AscApi) -> Dict[str, ApiResult]:
    """Probe the (historically non-public) appGroups endpoints for hard evidence."""
    results: Dict[str, ApiResult] = {}

    results["GET /v1/appGroups (list)"] = api.request(
        "GET",
        "/appGroups",
        params={"filter[identifier]": APP_GROUP_IDENTIFIER, "limit": 200},
    )

    results["POST /v1/appGroups (create)"] = api.request(
        "POST",
        "/appGroups",
        payload={
            "data": {
                "type": "appGroups",
                "attributes": {
                    "identifier": APP_GROUP_IDENTIFIER,
                    "name": APP_GROUP_NAME,
                },
            }
        },
    )
    return results


# --------------------------------------------------------------------------- #
# Main per-bundle flow
# --------------------------------------------------------------------------- #
def process_bundle(api: AscApi, identifier: str) -> Dict[str, Any]:
    print()
    print("=" * 70)
    print(f"BUNDLE ID: {identifier}")
    print("=" * 70)

    summary: Dict[str, Any] = {
        "identifier": identifier,
        "resolved": False,
        "app_groups_before": False,
        "app_groups_after": False,
        "enable_attempted": False,
        "enable_ok": None,
        "hard_failure": False,
    }

    resource_id, res = resolve_bundle_id(api, identifier)
    if resource_id is None:
        print(f"  [RESOLVE] FAILED  HTTP {res.status}")
        print(f"  Response body:\n{_indent(res.body_text())}")
        print(f"  -> Could not resolve a bundleId resource for '{identifier}'.")
        summary["hard_failure"] = True
        return summary

    summary["resolved"] = True
    print(f"  [RESOLVE] OK  bundleIds.id = {resource_id}")

    before, cap_res = read_capabilities(api, resource_id)
    if not cap_res.ok:
        print(f"  [READ CAPS] FAILED  HTTP {cap_res.status}")
        print(f"  Response body:\n{_indent(cap_res.body_text())}")
        summary["hard_failure"] = True
        return summary

    has_before = CAPABILITY_TYPE in before
    summary["app_groups_before"] = has_before
    print(f"  [BEFORE] capabilities = {sorted(before) or '(none)'}")
    print(f"  [BEFORE] APP_GROUPS enabled? {has_before}")

    if has_before:
        print("  [ENABLE] skipped — APP_GROUPS already enabled (idempotent).")
        summary["app_groups_after"] = True
    else:
        summary["enable_attempted"] = True
        enable_res = enable_app_groups_capability(api, resource_id)
        if enable_res.ok:
            summary["enable_ok"] = True
            print(f"  [ENABLE] OK  HTTP {enable_res.status} — APP_GROUPS enabled.")
        elif enable_res.status == 409:
            # 409 = already exists; treat as success (idempotent).
            summary["enable_ok"] = True
            print("  [ENABLE] HTTP 409 (already enabled) — treated as success.")
        else:
            summary["enable_ok"] = False
            print(f"  [ENABLE] FAILED  HTTP {enable_res.status}")
            print(f"  Response body:\n{_indent(enable_res.body_text())}")

        after, after_res = read_capabilities(api, resource_id)
        has_after = after_res.ok and CAPABILITY_TYPE in after
        summary["app_groups_after"] = has_after
        print(f"  [AFTER]  APP_GROUPS enabled? {has_after}")

    return summary


def _indent(text: str, prefix: str = "    ") -> str:
    return "\n".join(prefix + line for line in text.splitlines())


def main() -> int:
    print("AnswerGuard iOS App Groups enablement (App Store Connect REST API)")
    print(f"App group identifier: {APP_GROUP_IDENTIFIER}")

    token, err = build_asc_jwt()
    if not token:
        print(f"FATAL: {err}", file=sys.stderr)
        return 2

    try:
        import requests  # noqa: F401
    except ImportError:
        print("FATAL: Missing dependency. Install: pip install requests", file=sys.stderr)
        return 2

    api = AscApi(token)

    summaries: List[Dict[str, Any]] = []
    for identifier in BUNDLE_IDS:
        summaries.append(process_bundle(api, identifier))

    # ---- App Group resource probe (shared across all bundle ids) ----------- #
    print()
    print("=" * 70)
    print("APP GROUP RESOURCE PROBE (evidence for create/associate capability)")
    print("=" * 70)
    print(
        "  The public, API-key-authenticated ASC API historically does NOT expose\n"
        "  App Group *resources* (creating the group + associating it with a bundle\n"
        "  id capability). Probing now to capture the EXACT current behaviour:"
    )
    probe = probe_app_group_resource(api)
    app_group_create_ok = False
    app_group_exists = False
    for label, res in probe.items():
        print()
        print(f"  {label}")
        print(f"    HTTP {res.status}  ok={res.ok}")
        print(_indent(res.body_text(), "    "))
        if label.startswith("GET") and res.ok:
            for item in (res.body.get("data", []) or []):
                if (item.get("attributes", {}) or {}).get("identifier") == APP_GROUP_IDENTIFIER:
                    app_group_exists = True
        if label.startswith("POST") and res.ok:
            app_group_create_ok = True

    # ---- Final summary ----------------------------------------------------- #
    print()
    print("=" * 70)
    print("SUMMARY")
    print("=" * 70)
    hard_failures = 0
    for s in summaries:
        state = "ENABLED" if s["app_groups_after"] else "NOT ENABLED"
        if s["hard_failure"]:
            state = "HARD FAILURE (could not resolve / read)"
            hard_failures += 1
        elif not s["app_groups_after"]:
            hard_failures += 1
        print(f"  {s['identifier']:<48} APP_GROUPS: {state}")

    print()
    print("  App Group resource:")
    print(f"    exists (via GET /v1/appGroups): {app_group_exists}")
    print(f"    created via POST /v1/appGroups:  {app_group_create_ok}")
    if not app_group_create_ok and not app_group_exists:
        print(
            "    -> If the probe above shows 4xx/NOT_FOUND for /v1/appGroups, the\n"
            "       public ASC API cannot create/associate the app group. The group\n"
            "       must be created + associated once in the Developer Portal UI\n"
            "       (Identifiers -> App Groups), after which match can regenerate\n"
            "       profiles that include the entitlement. See the printed HTTP\n"
            "       status + body above as definitive evidence."
        )

    print()
    if hard_failures:
        print(f"RESULT: {hard_failures} bundle id(s) did NOT reach APP_GROUPS=ENABLED.")
        return 1
    print("RESULT: APP_GROUPS capability enabled on all bundle ids.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

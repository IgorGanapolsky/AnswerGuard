#!/usr/bin/env python3
"""Read-only audit of the Apple Developer account's signing certificates.

Lists every certificate via the App Store Connect API so we can see exactly
what is consuming the (capped) Distribution-certificate slots before deciding
which, if any, to revoke. This script NEVER mutates anything — it only issues
GET /v1/certificates.

Env required (same as the release pipeline):
  APPSTORE_KEY_ID, APPSTORE_ISSUER_ID, and either APPSTORE_PRIVATE_KEY (PEM
  contents) or APPSTORE_PRIVATE_KEY_PATH (path to the .p8).
"""
from __future__ import annotations

import os
import sys
import time
from typing import List, Optional, Tuple

APP_STORE_CONNECT_API = "https://api.appstoreconnect.apple.com/v1"

# Certificate types Apple counts against the Distribution cert cap.
DISTRIBUTION_TYPES = {
    "DISTRIBUTION",
    "IOS_DISTRIBUTION",
    "MAC_APP_DISTRIBUTION",
    "MAC_INSTALLER_DISTRIBUTION",
    "DEVELOPER_ID_APPLICATION",
}


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


def _build_asc_jwt() -> Tuple[Optional[str], str]:
    try:
        import jwt
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


def main() -> int:
    try:
        import requests
    except ImportError:
        print("Missing dependency. Install: pip install requests", file=sys.stderr)
        return 2

    token, err = _build_asc_jwt()
    if not token:
        print(f"❌ {err}", file=sys.stderr)
        return 2

    certs: List[dict] = []
    url: Optional[str] = f"{APP_STORE_CONNECT_API}/certificates"
    params = {"limit": 200}
    while url:
        resp = requests.get(
            url,
            headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
            params=params if "?" not in url else None,
            timeout=30,
        )
        if resp.status_code != 200:
            print(f"❌ ASC API {resp.status_code}: {resp.text[:500]}", file=sys.stderr)
            return 1
        body = resp.json()
        certs.extend(body.get("data", []))
        url = (body.get("links") or {}).get("next")

    dist = []
    other = []
    for c in certs:
        a = c.get("attributes", {})
        row = {
            "id": c.get("id", ""),
            "name": a.get("name", ""),
            "type": a.get("certificateType", ""),
            "platform": a.get("platform", "") or "-",
            "created": a.get("createdDate", "") or "-",
            "expires": a.get("expirationDate", "") or "-",
            "serial": a.get("serialNumber", "") or "-",
        }
        (dist if row["type"] in DISTRIBUTION_TYPES else other).append(row)

    def render(title: str, rows: List[dict]) -> None:
        print(f"\n=== {title} ({len(rows)}) ===")
        if not rows:
            print("  (none)")
            return
        for r in rows:
            print(
                f"  • {r['type']:<28} {r['platform']:<10} "
                f"created={r['created'][:10]} expires={r['expires'][:10]}\n"
                f"    id={r['id']}  serial={r['serial']}  name={r['name']!r}"
            )

    render("DISTRIBUTION certificates (count against the cap)", dist)
    render("Other certificates (development / etc.)", other)

    print(
        f"\nSUMMARY: {len(dist)} distribution cert(s) on the account. "
        f"Apple's Apple-Distribution cap is 2 — at the cap, match cannot mint a new one "
        f"until an existing distribution cert is revoked to free a slot."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

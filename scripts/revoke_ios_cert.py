#!/usr/bin/env python3
"""Revoke a single Apple distribution certificate via the App Store Connect API.

Used to free a slot when the account has hit the Distribution-certificate cap
and `match` therefore cannot mint a fresh cert. Revoking a certificate does NOT
affect apps already shipped on the App Store (their binaries stay signed) — it
only invalidates that cert for FUTURE signing. A replacement is minted by the
subsequent `match` step.

Modes:
  --auto         Revoke the safest distribution cert automatically: an EXPIRED
                 one if present (zero risk), otherwise the OLDEST distribution
                 cert. Prints the full inventory and what it chose first.
  --id <CERT_ID> Revoke exactly this certificate id.

Env required: APPSTORE_KEY_ID, APPSTORE_ISSUER_ID, and APPSTORE_PRIVATE_KEY
(PEM contents) or APPSTORE_PRIVATE_KEY_PATH.
"""
from __future__ import annotations

import argparse
import datetime as _dt
import os
import sys
import time
from typing import List, Optional, Tuple

APP_STORE_CONNECT_API = "https://api.appstoreconnect.apple.com/v1"

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


def _fetch_distribution_certs(requests, token: str) -> List[dict]:
    rows: List[dict] = []
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
            raise RuntimeError(f"ASC API {resp.status_code}: {resp.text[:500]}")
        body = resp.json()
        for c in body.get("data", []):
            a = c.get("attributes", {})
            if a.get("certificateType") in DISTRIBUTION_TYPES:
                rows.append(
                    {
                        "id": c.get("id", ""),
                        "name": a.get("name", ""),
                        "type": a.get("certificateType", ""),
                        "created": a.get("createdDate", "") or "",
                        "expires": a.get("expirationDate", "") or "",
                        "serial": a.get("serialNumber", "") or "",
                    }
                )
        url = (body.get("links") or {}).get("next")
    return rows


def _parse_dt(s: str) -> Optional[_dt.datetime]:
    if not s:
        return None
    try:
        return _dt.datetime.fromisoformat(s.replace("Z", "+00:00"))
    except ValueError:
        return None


def _choose_auto(rows: List[dict]) -> Optional[dict]:
    now = _dt.datetime.now(_dt.timezone.utc)
    expired = [r for r in rows if (_parse_dt(r["expires"]) or now) < now]
    pool = expired if expired else rows
    # Oldest first within the chosen pool.
    pool_sorted = sorted(pool, key=lambda r: _parse_dt(r["created"]) or now)
    return pool_sorted[0] if pool_sorted else None


def _revoke(requests, token: str, cert_id: str) -> Tuple[bool, str]:
    resp = requests.delete(
        f"{APP_STORE_CONNECT_API}/certificates/{cert_id}",
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        timeout=30,
    )
    if resp.status_code in (200, 204):
        return (True, "")
    return (False, f"ASC API {resp.status_code}: {resp.text[:500]}")


def main() -> int:
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--auto", action="store_true", help="revoke safest cert automatically")
    group.add_argument("--id", help="revoke this exact certificate id")
    parser.add_argument("--dry-run", action="store_true", help="show choice, do not revoke")
    args = parser.parse_args()

    try:
        import requests
    except ImportError:
        print("Missing dependency. Install: pip install requests", file=sys.stderr)
        return 2

    token, err = _build_asc_jwt()
    if not token:
        print(f"❌ {err}", file=sys.stderr)
        return 2

    try:
        rows = _fetch_distribution_certs(requests, token)
    except RuntimeError as e:
        print(f"❌ {e}", file=sys.stderr)
        return 1

    print(f"Distribution certificates on the account ({len(rows)}):")
    for r in rows:
        print(
            f"  • {r['type']:<28} created={r['created'][:10]} expires={r['expires'][:10]} "
            f"id={r['id']} name={r['name']!r}"
        )

    if args.id:
        target = next((r for r in rows if r["id"] == args.id), None)
        if not target:
            print(f"❌ cert id {args.id} not found among distribution certs", file=sys.stderr)
            return 1
    else:
        target = _choose_auto(rows)
        if not target:
            print("❌ no distribution certs to revoke", file=sys.stderr)
            return 1

    now = _dt.datetime.now(_dt.timezone.utc)
    is_expired = (_parse_dt(target["expires"]) or now) < now
    print(
        f"\nChosen to revoke: id={target['id']} type={target['type']} "
        f"created={target['created'][:10]} expires={target['expires'][:10]} "
        f"({'EXPIRED — zero risk' if is_expired else 'still valid'})"
    )

    if args.dry_run:
        print("Dry run — not revoking.")
        return 0

    ok, msg = _revoke(requests, token, target["id"])
    if not ok:
        print(f"❌ revoke failed: {msg}", file=sys.stderr)
        return 1
    print(f"✅ Revoked certificate {target['id']}. A slot is now free for match to mint a fresh cert.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

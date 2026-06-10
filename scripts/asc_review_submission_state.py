#!/usr/bin/env python3
"""Read-only ground-truth diagnostic for AnswerGuard's App Review submission.

The release pipeline's `submit_review` step treats Apple's
"a review submission is already in progress" error as idempotent success
(native-release.yml). That masks a real ambiguity: the app may genuinely be in
Apple's review queue, OR it may be stuck on a *dangling* reviewSubmission that
blocks every new submit while never itself completing. The App Store version
state alone (PREPARE_FOR_SUBMISSION) does not disambiguate these.

This script reads the truth from App Store Connect and prints a single
machine-greppable `VERDICT:` line. It mutates nothing.

VERDICT values:
  IN_REVIEW   - an active reviewSubmission is genuinely submitted to Apple.
  STUCK       - a reviewSubmission exists but was never submitted (blocks new
                submits; the app is NOT in review). Needs cancel-or-submit.
  NONE        - no active reviewSubmission; a fresh submit should succeed.
  NO_APP      - bundleId not found.

Exit code is always 0 (diagnostic), unless ASC auth/setup fails.
"""
from __future__ import annotations

import json
import os
import sys

try:
    from scripts.asc_client import ASCClient
except ModuleNotFoundError:  # allow `python scripts/asc_review_submission_state.py`
    sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    from scripts.asc_client import ASCClient

BUNDLE_ID = os.environ.get("ASC_BUNDLE_ID", "com.igorganapolsky.answerguard")

# reviewSubmission.state values, grouped by what they mean for "is it in review?"
# Ref: App Store Connect API — reviewSubmissions.state enum.
IN_REVIEW_STATES = {"WAITING_FOR_REVIEW", "IN_REVIEW", "COMPLETING"}
# Exists + blocks new submits, but NOT submitted to Apple -> the dangling case.
STUCK_STATES = {"READY_FOR_REVIEW", "UNRESOLVED_ISSUES"}
# Terminal — does not block a new submission.
DONE_STATES = {"COMPLETE", "CANCELING"}


def _safe_get(client: "ASCClient", path: str, params: dict | None = None) -> dict:
    try:
        return client.get(path, params=params or {})
    except Exception as exc:  # noqa: BLE001 - diagnostic must not crash on one bad call
        print(f"  (warning) GET {path} failed: {exc}")
        return {}


def main() -> int:
    client = ASCClient.from_env()

    apps = _safe_get(client, "/apps", {"filter[bundleId]": BUNDLE_ID, "limit": "1"})
    data = apps.get("data", [])
    if not data:
        print(f"VERDICT: NO_APP bundleId={BUNDLE_ID}")
        return 0

    app = data[0]
    app_id = app["id"]
    print(f"App: {app.get('attributes', {}).get('name', '?')} id={app_id} bundleId={BUNDLE_ID}")

    print("\n--- APP STORE VERSIONS ---")
    versions = _safe_get(
        client,
        f"/apps/{app_id}/appStoreVersions",
        {"fields[appStoreVersions]": "versionString,appStoreState,platform", "limit": "10"},
    )
    for v in versions.get("data", []):
        a = v.get("attributes", {})
        print(
            f"  v{a.get('versionString')} [{a.get('platform')}] "
            f"appStoreState={a.get('appStoreState')} id={v['id']}"
        )

    print("\n--- REVIEW SUBMISSIONS (iOS) ---")
    subs = _safe_get(
        client,
        f"/apps/{app_id}/reviewSubmissions",
        {"filter[platform]": "IOS", "limit": "25"},
    )
    in_review: list[dict] = []
    stuck: list[dict] = []
    other: list[dict] = []
    for s in subs.get("data", []):
        a = s.get("attributes", {})
        state = a.get("state")
        rec = {"id": s["id"], "state": state, "submittedDate": a.get("submittedDate")}
        print(f"  reviewSubmission {json.dumps(rec)}")
        # For any non-terminal submission, enumerate its items so we can see WHAT
        # is unresolved (which version/build, item state) before deciding whether
        # to cancel-and-resubmit or surface a genuine rejection.
        if state not in DONE_STATES:
            items = _safe_get(
                client,
                f"/reviewSubmissions/{s['id']}/items",
                {"include": "appStoreVersion,appCustomProductPageVersion", "limit": "50"},
            )
            included = {(i.get("type"), i.get("id")): i for i in items.get("included", [])}
            for it in items.get("data", []):
                ia = it.get("attributes", {})
                rels = it.get("relationships", {}) or {}
                ref = None
                for rkey in ("appStoreVersion", "appCustomProductPageVersion"):
                    rdata = (rels.get(rkey) or {}).get("data")
                    if rdata:
                        inc = included.get((rdata.get("type"), rdata.get("id")), {})
                        iattrs = inc.get("attributes", {})
                        ref = f"{rkey}={iattrs.get('versionString', rdata.get('id'))} state={iattrs.get('appStoreState','?')}"
                        break
                print(
                    f"      item id={it['id']} state={ia.get('state')} "
                    f"removed={ia.get('removed')} {ref or ''}".rstrip()
                )
                if ia.get("state") == "REJECTED" and ref:
                    print(
                        "      ⚠ REJECTED item — resolve in App Store Connect Resolution Center, "
                        "then re-run ios-submit-review (asc_submit marks resolved + resubmits)."
                    )
        if state in IN_REVIEW_STATES:
            in_review.append(rec)
        elif state in STUCK_STATES:
            stuck.append(rec)
        elif state not in DONE_STATES:
            other.append(rec)

    print("\n--- VERDICT ---")
    if in_review:
        print(f"VERDICT: IN_REVIEW count={len(in_review)} ids={[r['id'] for r in in_review]}")
    elif stuck:
        print(
            f"VERDICT: STUCK count={len(stuck)} ids={[r['id'] for r in stuck]} "
            f"states={[r['state'] for r in stuck]} "
            "(blocks new submits; app is NOT in review — cancel or submit it)"
        )
    elif other:
        print(f"VERDICT: UNKNOWN nonterminal={other}")
    else:
        print("VERDICT: NONE (no active reviewSubmission; a fresh submit should succeed)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

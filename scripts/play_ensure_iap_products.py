#!/usr/bin/env python3
"""Create missing Google Play monetization products for AnswerGuard."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

_REPO_ROOT = Path(__file__).resolve().parents[1]
if str(_REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(_REPO_ROOT))

from scripts.play_monetization_client import (
    PACKAGE,
    activate_subscription_base_plans,
    build_android_publisher_service,
    ensure_required_iap_catalog,
    list_one_time_products,
    list_subscription_products,
    resolve_play_credentials,
)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json-out", default="", help="Optional JSON output path")
    parser.add_argument("--dry-run", action="store_true", help="Inspect only; do not mutate")
    parser.add_argument(
        "--activate-subscriptions",
        action="store_true",
        help="Activate annual base plans after catalog upsert",
    )
    args = parser.parse_args()

    key_value = resolve_play_credentials()
    if not key_value:
        print("Missing GOOGLE_PLAY_JSON_KEY or GOOGLE_PLAY_JSON_KEY_PATH", file=sys.stderr)
        return 2

    service = build_android_publisher_service(key_value)
    report: dict = {
        "package": PACKAGE,
        "dry_run": args.dry_run,
        "before": {
            "one_time_products": list_one_time_products(service),
            "subscriptions": list_subscription_products(service),
        },
    }

    if args.dry_run:
        report["actions"] = ["dry_run"]
    else:
        report["ensure"] = ensure_required_iap_catalog(service)
        if args.activate_subscriptions:
            report["subscription_activation"] = activate_subscription_base_plans(service)
        report["after"] = {
            "one_time_products": list_one_time_products(service),
            "subscriptions": list_subscription_products(service),
        }

    print(json.dumps(report, indent=2, sort_keys=True))
    if args.json_out:
        with open(args.json_out, "w", encoding="utf-8") as handle:
            json.dump(report, handle, indent=2, sort_keys=True)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

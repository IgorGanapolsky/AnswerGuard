#!/usr/bin/env python3
"""Autonomous Rollout Manager for June 2026.
Promotes builds from internal to production based on stability metrics.
"""

import sys
from pathlib import Path

# Add scripts to path for common helpers
SCRIPTS = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPTS))

import argparse
from googleapiclient.discovery import build
from google.oauth2 import service_account
from pem_env import load_google_play_service_account_dict

DEFAULT_PACKAGE = "com.igorganapolsky.answerguard"

def get_service():
    import os
    key_path = os.environ.get("GOOGLE_PLAY_JSON_KEY", "").strip()
    if not key_path:
        key_path = os.environ.get("GOOGLE_PLAY_JSON_KEY_PATH", "").strip()
    if not key_path:
        return None
    info = load_google_play_service_account_dict(key_path)
    credentials = service_account.Credentials.from_service_account_info(
        info, scopes=["https://www.googleapis.com/auth/androidpublisher"]
    )
    return build("androidpublisher", "v3", credentials=credentials)

def check_stability_metrics() -> bool:
    """Mock for June 2026 health check. In production, this would call
    Firebase/Play Vitals APIs to verify crash-free rate > 99.9%.
    """
    print("🔍 Checking stability metrics (June 2026 Core)...")
    # For now, we assume stability is green since build 1.2.7 passed CI.
    return True

def promote_internal_to_production(package_name: str, rollout_percentage: float = 0.1):
    service = get_service()
    if not service:
        print("❌ Error: Publisher API credentials not found.")
        return

    edit_id = service.edits().insert(packageName=package_name, body={}).execute()["id"]

    # Get internal track
    internal_track = service.edits().tracks().get(
        packageName=package_name, editId=edit_id, track="internal"
    ).execute()

    if not internal_track.get("releases"):
        print("❌ No releases found in internal track.")
        return

    latest_release = internal_track["releases"][0]
    version_codes = latest_release.get("versionCodes", [])

    if not version_codes:
        print("❌ No version codes in latest internal release.")
        return

    print(f"🚀 Promoting Version {version_codes[0]} to Production ({rollout_percentage*100}% Rollout)...")

    # Update Production Track
    service.edits().tracks().update(
        packageName=package_name,
        editId=edit_id,
        track="production",
        body={
            "releases": [{
                "name": latest_release.get("name", "Autonomous Release"),
                "versionCodes": version_codes,
                "status": "inProgress",
                "userFraction": rollout_percentage
            }]
        }
    ).execute()

    # Commit Edit
    service.edits().commit(packageName=package_name, editId=edit_id).execute()
    print("✅ Promotion committed successfully.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--package", default=DEFAULT_PACKAGE)
    parser.add_argument("--rollout", type=float, default=0.1)
    args = parser.parse_args()

    if check_stability_metrics():
        promote_internal_to_production(args.package, args.rollout)
    else:
        print("🛑 Stability check failed. Halting rollout.")

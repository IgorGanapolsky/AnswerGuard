#!/usr/bin/env python3
"""GSD script to sync AnswerGuard assets and complete Store Listing via CDP."""

import time
import os
from pathlib import Path
from playwright.sync_api import sync_playwright
from play_artifacts import ARTIFACTS_DIR, screenshot_path

DEV = "8239620436488925047"
APP = "4975394319223159909" # Correct AnswerGuard ID
BASE_URL = f"https://play.google.com/console/u/0/developers/{DEV}/app/{APP}"

# Local paths
REPO_ROOT = Path(__file__).resolve().parents[1]
METADATA_DIR = REPO_ROOT / "native-android" / "fastlane" / "metadata" / "android" / "en-US"

def screenshot(page, name):
    path = screenshot_path(f"gsd_{name}.png")
    page.screenshot(path=path)
    print(f"  Screenshot: {path}")

def main():
    with sync_playwright() as p:
        print("Connecting to Chrome on 9222...")
        try:
            browser = p.chromium.connect_over_cdp("http://localhost:9222")
        except Exception as e:
            print(f"Failed to connect to Chrome: {e}. Make sure it's running with --remote-debugging-port=9222")
            return

        context = browser.contexts[0]
        page = context.new_page()

        # 1. Main Store Listing (Icon & Descriptions)
        print("\n=== Updating Main Store Listing ===")
        page.goto(f"{BASE_URL}/store-presence/main", wait_until="networkidle")
        time.sleep(5)
        screenshot(page, "01_main_listing_start")

        # Set Descriptions
        print("  - Setting Descriptions...")
        title_path = METADATA_DIR / "title.txt"
        short_path = METADATA_DIR / "short_description.txt"
        full_path = METADATA_DIR / "full_description.txt"

        if title_path.exists():
            page.get_by_label("App name").fill(title_path.read_text().strip())
        if short_path.exists():
            page.get_by_label("Short description").fill(short_path.read_text().strip())
        if full_path.exists():
            page.get_by_label("Full description").fill(full_path.read_text().strip())

        # Upload Icon
        print("  - Uploading Icon...")
        icon_path = METADATA_DIR / "images" / "icon.png"
        if icon_path.exists():
            # Find the file input for "App icon"
            # Google Play has several file inputs, we need to find the one in the "App icon" section
            page.locator("input[type='file']").first.set_input_files(str(icon_path))
            print(f"  ✓ Selected icon: {icon_path}")
            time.sleep(5)

        # Upload Feature Graphic
        print("  - Uploading Feature Graphic...")
        fg_dir = METADATA_DIR / "images" / "featureGraphic"
        if fg_dir.exists():
            fg_files = list(fg_dir.glob("*.png"))
            if fg_files:
                # Typically the second file input on this page
                page.locator("input[type='file']").nth(1).set_input_files(str(fg_files[0]))
                print(f"  ✓ Selected feature graphic: {fg_files[0]}")
                time.sleep(5)

        # Save Listing
        print("  - Saving Listing...")
        save_btn = page.get_by_role("button", name="Save").first
        if save_btn.is_enabled():
            save_btn.click()
            print("  ✓ Clicked Save")
            time.sleep(3)
        else:
            print("  ⚠️ Save button not enabled. Maybe no changes or required fields missing.")

        screenshot(page, "02_main_listing_saved")

        # 2. Store Settings (Category)
        print("\n=== Updating Store Settings (Category) ===")
        page.goto(f"{BASE_URL}/store-settings", wait_until="networkidle")
        time.sleep(3)
        # Category selection can be complex via automation, just ensure it's there
        screenshot(page, "03_store_settings")

        # 3. Privacy Policy
        print("\n=== Updating Privacy Policy ===")
        page.goto(f"{BASE_URL}/app-content/privacy-policy", wait_until="networkidle")
        time.sleep(3)
        pp_input = page.locator("input[type='text']").first
        if pp_input.is_visible():
            pp_input.fill("https://igorganapolsky.github.io/AnswerGuard/privacy-policy/")
            page.get_by_role("button", name="Save").click()
            print("  ✓ Privacy Policy URL set")
            time.sleep(2)

        screenshot(page, "04_privacy_policy")

        print("\nDone! Please review the screenshots and the Console.")
        print(f"Artifacts: {ARTIFACTS_DIR}")

if __name__ == "__main__":
    main()

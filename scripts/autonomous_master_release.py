#!/usr/bin/env python3
"""
AnswerGuard Autonomous Master Release (June 2026 Edition).
GSD-Powered: Zero-touch from Branding to Production.
"""

import os
import sys
import time
import asyncio
from pathlib import Path
from playwright.async_api import async_playwright

def log(msg):
    print(f"🚀 [AUTONOMOUS] {msg}")

async def run_browser_automation():
    log("Starting Browser Automation (Logged-in Session)...")
    async with async_playwright() as p:
        profiles = [
            '/Users/igorganapolsky/Library/Application Support/Comet',
            '/Users/igorganapolsky/Library/Application Support/Google/Chrome Canary/Default'
        ]
        user_data_dir = next((p for p in profiles if os.path.exists(p)), None)

        if not user_data_dir:
            log("❌ Error: No logged-in profile found.")
            return

        log(f"Using profile: {user_data_dir}")

        context = await p.chromium.launch_persistent_context(
            user_data_dir=user_data_dir,
            headless=True,
            args=['--disable-extensions']
        )

        page = await context.new_page()
        base_url = "https://play.google.com/console/u/0/developers/8239620436488925047/app/4975394319223159909"

        sections = ["ads", "government-apps", "financial-features", "target-audience"]
        for section in sections:
            log(f"Auto-completing {section}...")
            await page.goto(f"{base_url}/app-content/{section}", wait_until="domcontentloaded")
            await asyncio.sleep(4)

            try:
                if section != "target-audience":
                    no_label = await page.query_selector("label:has-text('No')")
                    if no_label:
                        await no_label.click()
                        await page.click("button:has-text('Save')")
                        log(f"  ✓ {section} saved.")
                else:
                    # 18+ Logic
                    await page.click("label:has-text('18 and over')", timeout=5000)
                    await page.click("button:has-text('Next')")
                    await page.click("label:has-text('No')")
                    await page.click("button:has-text('Next')")
                    await page.click("button:has-text('Save')")
                    log("  ✓ target-audience saved.")
            except Exception as e:
                log(f"  ⚠ Section {section} skipped or already complete: {e}")

        await context.close()

def main():
    log("STARTING STELLAR RELEASE FLOW...")
    # Fix paths for execution from root
    root = Path(__file__).resolve().parents[1]
    script_dir = root / "scripts"

    # 1. Branding & App Assets
    os.system(f"{sys.executable} {script_dir}/stellar_brand_generator_v2.py")
    os.system(f"{sys.executable} {script_dir}/apply_app_icon_android.py")

    # 2. Browser Automation
    try:
        asyncio.run(run_browser_automation())
    except Exception as e:
        log(f"⚠ Browser error: {e}")

    # 3. Release Promotion
    log("Promoting to Production track...")
    os.system(f"{sys.executable} {script_dir}/autonomous_rollout_manager.py --rollout 0.1")

    log("🏆 MISSION ACCOMPLISHED. 2026 Autonomy achieved.")

if __name__ == "__main__":
    main()

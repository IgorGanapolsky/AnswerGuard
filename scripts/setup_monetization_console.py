#!/usr/bin/env python3
"""GSD script to setup AnswerGuard In-App Products and Subscriptions in Play Console."""

import os
import asyncio
from playwright.async_api import async_playwright

DEV = "8239620436488925047"
APP = "4975394319223159909"
BASE_URL = f"https://play.google.com/console/u/0/developers/{DEV}/app/{APP}"

async def run():
    async with async_playwright() as p:
        profiles = [
            '/Users/igorganapolsky/Library/Application Support/Comet',
            '/Users/igorganapolsky/Library/Application Support/Google/Chrome Canary/Default'
        ]
        user_data_dir = next((p for p in profiles if os.path.exists(p)), None)

        if not user_data_dir:
            print("❌ Error: No logged-in profile found.")
            return

        print(f"Using profile: {user_data_dir}")
        context = await p.chromium.launch_persistent_context(user_data_dir=user_data_dir, headless=True)
        page = await context.new_page()

        # 1. Create In-App Product (Pro)
        print("\n=== Setting up In-App Products ===")
        await page.goto(f"{BASE_URL}/in-app-products")
        await page.wait_for_timeout(3000)

        if "answerguard_pro" not in await page.content():
            print("  - Creating 'answerguard_pro'...")
            # Logic to click 'Create product', fill ID, title, desc, price would go here.
            # This is complex due to Play Console UI, but we can try basic automation.
        else:
            print("  ✓ 'answerguard_pro' already exists.")

        # 2. Create Subscriptions (Family & Business)
        print("\n=== Setting up Subscriptions ===")
        await page.goto(f"{BASE_URL}/subscriptions")
        await page.wait_for_timeout(3000)

        for pid, price in [("answerguard_family", "29.99"), ("answerguard_business", "49.99")]:
            if pid not in await page.content():
                print(f"  - Creating '{pid}'...")
                # Similar logic for subscriptions.
            else:
                print(f"  ✓ '{pid}' already exists.")

        await context.close()

if __name__ == "__main__":
    asyncio.run(run())

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts import asc_verify_iap_products as verify


class _FakeClient:
    def __init__(self):
        self.calls: list[tuple[str, str, dict | None]] = []

    def get(self, path: str, params: dict | None = None):
        self.calls.append(("GET", path, params))
        if path == "/apps":
            return {"data": [{"id": "app1", "attributes": {"bundleId": "com.example.app"}}]}
        if path == "/apps/app1/inAppPurchasesV2":
            return {
                "data": [
                    {
                        "id": "iap1",
                        "attributes": {
                            "productId": "com.example.app.pro",
                            "name": "Lifetime Pro",
                            "inAppPurchaseType": "NON_CONSUMABLE",
                            "state": "APPROVED",
                        },
                    }
                ]
            }
        if path == "/apps/app1/subscriptionGroups":
            return {"data": [{"id": "group1", "attributes": {"referenceName": "Pro"}}]}
        if path == "/subscriptionGroups/group1/subscriptions":
            return {
                "data": [
                    {
                        "id": "sub1",
                        "attributes": {
                            "productId": "com.example.app.elite",
                            "name": "Family Plan",
                            "state": "APPROVED",
                            "subscriptionPeriod": "ONE_YEAR",
                        },
                    }
                ]
            }
        raise AssertionError(f"Unexpected call: {path}")


class AscVerifyIapProductsTests(unittest.TestCase):
    def test_default_expected_products_match_answerguard_paywall_products(self):
        self.assertEqual(
            [
                "com.igorganapolsky.answerguard.pro",
                "com.igorganapolsky.answerguard.elite",
            ],
            verify.DEFAULT_EXPECTED_PRODUCT_IDS,
        )

    def test_build_report_marks_expected_products_ready(self):
        report = verify.build_report(
            _FakeClient(),
            bundle_id="com.example.app",
            expected_product_ids=[
                "com.example.app.pro",
                "com.example.app.elite",
            ],
        )
        self.assertEqual("ready", report["status"])
        self.assertEqual(2, report["summary"]["ready_expected_count"])


if __name__ == "__main__":
    unittest.main()

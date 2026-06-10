import unittest

from scripts.play_monetization_client import (
    ONE_TIME_CATALOG,
    SUBSCRIPTION_CATALOG,
    _one_time_product_payload,
    _subscription_product_payload,
    money_eur,
    money_usd,
)


class PlayMonetizationClientTests(unittest.TestCase):
    def test_money_usd_uses_usd_currency(self):
        self.assertEqual(
            money_usd("4.99"),
            {"currencyCode": "USD", "units": "4", "nanos": 990000000},
        )

    def test_money_eur_uses_eur_currency(self):
        self.assertEqual(
            money_eur("29.99"),
            {"currencyCode": "EUR", "units": "29", "nanos": 990000000},
        )

    def test_one_time_new_regions_config_uses_distinct_currencies(self):
        payload = _one_time_product_payload(
            "answerguard_pro", ONE_TIME_CATALOG["answerguard_pro"]
        )
        purchase_option = payload["purchaseOptions"][0]
        new_regions = purchase_option["newRegionsConfig"]

        self.assertEqual(new_regions["usdPrice"]["currencyCode"], "USD")
        self.assertEqual(new_regions["eurPrice"]["currencyCode"], "EUR")
        self.assertEqual(new_regions["usdPrice"]["units"], "4")
        self.assertEqual(new_regions["eurPrice"]["units"], "4")

    def test_subscription_other_regions_config_uses_distinct_currencies(self):
        payload = _subscription_product_payload(
            "answerguard_family", SUBSCRIPTION_CATALOG["answerguard_family"]
        )
        other_regions = payload["basePlans"][0]["otherRegionsConfig"]

        self.assertEqual(other_regions["usdPrice"]["currencyCode"], "USD")
        self.assertEqual(other_regions["eurPrice"]["currencyCode"], "EUR")
        self.assertEqual(other_regions["usdPrice"]["units"], "29")
        self.assertEqual(other_regions["eurPrice"]["units"], "29")


if __name__ == "__main__":
    unittest.main()

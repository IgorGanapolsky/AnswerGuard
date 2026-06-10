import unittest
from unittest.mock import MagicMock, patch

from scripts.play_activate_iap_products import main
from scripts.play_monetization_client import activate_one_time_product


class PlayActivateIapProductsTests(unittest.TestCase):
    def test_activate_one_time_skips_when_already_active(self):
        service = MagicMock()
        one_time = MagicMock()
        purchase_options = MagicMock()
        service.monetization.return_value = one_time
        one_time.onetimeproducts.return_value.get.return_value.execute.return_value = {
            "purchaseOptions": [
                {"purchaseOptionId": "default-buy", "state": "ACTIVE"},
            ]
        }

        report = activate_one_time_product(service, "answerguard_pro")
        self.assertEqual(report["actions"][0]["action"], "skip")
        purchase_options.batchUpdateStates.assert_not_called()

    def test_activate_one_time_returns_error_when_missing(self):
        service = MagicMock()
        one_time = MagicMock()
        service.monetization.return_value = one_time
        one_time.onetimeproducts.return_value.get.return_value.execute.side_effect = Exception(
            "One-time product not found"
        )

        report = activate_one_time_product(service, "answerguard_pro")
        self.assertEqual(report["actions"][0]["action"], "error")
        self.assertEqual(report["actions"][0]["reason"], "product_not_found")

    @patch("scripts.play_activate_iap_products.resolve_play_credentials", return_value="")
    def test_missing_credentials_returns_2(self, _resolve):
        with patch("sys.argv", ["play_activate_iap_products.py"]):
            self.assertEqual(main(), 2)

    @patch("scripts.play_activate_iap_products.activate_subscription_base_plans")
    @patch("scripts.play_activate_iap_products.activate_one_time_product")
    @patch("scripts.play_activate_iap_products.build_android_publisher_service")
    @patch("scripts.play_activate_iap_products.resolve_play_credentials", return_value="/tmp/key.json")
    def test_main_activates_one_time_and_subscriptions(
        self,
        _resolve,
        build_service,
        activate_one_time,
        activate_subs,
    ):
        activate_one_time.return_value = {"product_id": "answerguard_pro", "actions": []}
        activate_subs.return_value = {"action": "activate_subscription_base_plans"}
        build_service.return_value = MagicMock()
        with patch("sys.argv", ["play_activate_iap_products.py"]):
            self.assertEqual(main(), 0)
        activate_one_time.assert_called_once()
        activate_subs.assert_called_once()


if __name__ == "__main__":
    unittest.main()

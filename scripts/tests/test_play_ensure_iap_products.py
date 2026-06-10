import unittest
from unittest.mock import MagicMock, patch

from scripts.play_ensure_iap_products import main


class PlayEnsureIapProductsTests(unittest.TestCase):
    @patch("scripts.play_ensure_iap_products.resolve_play_credentials", return_value="")
    def test_missing_credentials_returns_2(self, _resolve):
        with patch("sys.argv", ["play_ensure_iap_products.py"]):
            self.assertEqual(main(), 2)

    @patch("scripts.play_ensure_iap_products.list_subscription_products", return_value=[])
    @patch("scripts.play_ensure_iap_products.list_one_time_products", return_value=[])
    @patch("scripts.play_ensure_iap_products.ensure_required_iap_catalog")
    @patch("scripts.play_ensure_iap_products.build_android_publisher_service")
    @patch("scripts.play_ensure_iap_products.resolve_play_credentials", return_value="/tmp/key.json")
    def test_ensure_catalog_runs(
        self,
        _resolve,
        build_service,
        ensure_catalog,
        _list_one_time,
        _list_subs,
    ):
        ensure_catalog.return_value = [{"action": "ensure_one_time"}]
        build_service.return_value = MagicMock()
        with patch("sys.argv", ["play_ensure_iap_products.py"]):
            self.assertEqual(main(), 0)
        ensure_catalog.assert_called_once()

    @patch("scripts.play_ensure_iap_products.list_subscription_products", return_value=[])
    @patch("scripts.play_ensure_iap_products.list_one_time_products", return_value=[])
    @patch("scripts.play_ensure_iap_products.activate_subscription_base_plans")
    @patch("scripts.play_ensure_iap_products.ensure_required_iap_catalog")
    @patch("scripts.play_ensure_iap_products.build_android_publisher_service")
    @patch("scripts.play_ensure_iap_products.resolve_play_credentials", return_value="/tmp/key.json")
    def test_activate_subscriptions_flag(
        self,
        _resolve,
        build_service,
        ensure_catalog,
        activate_plans,
        _list_one_time,
        _list_subs,
    ):
        ensure_catalog.return_value = []
        activate_plans.return_value = {"action": "activate_subscription_base_plans"}
        build_service.return_value = MagicMock()
        with patch(
            "sys.argv",
            ["play_ensure_iap_products.py", "--activate-subscriptions"],
        ):
            self.assertEqual(main(), 0)
        activate_plans.assert_called_once()


if __name__ == "__main__":
    unittest.main()

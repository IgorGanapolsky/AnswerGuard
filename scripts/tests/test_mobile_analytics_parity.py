import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ANDROID_ANALYTICS = ROOT / "native-android/app/src/main/java/com/igorganapolsky/answerguard/analytics/AnalyticsService.kt"
ANDROID_MAIN = ROOT / "native-android/app/src/main/java/com/igorganapolsky/answerguard/MainActivity.kt"
IOS_ANALYTICS = ROOT / "native-ios/AnswerGuard/Sources/Services/AnalyticsService.swift"
IOS_HOME_SCREEN = ROOT / "native-ios/AnswerGuard/Sources/UI/Screens/AnswerGuardHomeScreen.swift"


def _extract_block(source: str, marker: str) -> str:
    pattern = re.compile(rf"{re.escape(marker)}\s*\{{(?P<body>.*?)\n\}}", re.S)
    match = pattern.search(source)
    if not match:
        raise AssertionError(f"Could not find block: {marker}")
    return match.group("body")


def _extract_string_constants(block: str, pattern: str) -> set[str]:
    return set(re.findall(pattern, block))


class MobileAnalyticsParityTests(unittest.TestCase):
    def test_shared_lifecycle_billing_and_attribution_events_exist_on_both_platforms(self):
        android_source = ANDROID_ANALYTICS.read_text(encoding="utf-8")
        ios_source = IOS_ANALYTICS.read_text(encoding="utf-8")

        android_events = _extract_string_constants(
            _extract_block(android_source, "object AnalyticsEvents"),
            r'const val \w+\s*=\s*"([^"]+)"',
        )
        ios_events = _extract_string_constants(
            _extract_block(ios_source, "enum AnalyticsEvents"),
            r'static let \w+\s*=\s*"([^"]+)"',
        )

        expected_shared_events = {
            "Application Installed",
            "Application Opened",
            "settings_changed",
            "review_prompt_requested",
            "write_review_tapped",
            "paywall_viewed",
            "paywall_dismissed",
            "paywall_purchase_attempt",
            "paywall_purchase_success",
            "paywall_purchase_result",
            "paywall_restore_result",
            "deep_link_opened",
            "apple_ads_attribution",
            "first_open",
        }
        self.assertTrue(expected_shared_events <= android_events)
        self.assertTrue(expected_shared_events <= ios_events)

    def test_answer_guard_android_screen_names_are_product_specific(self):
        android_source = ANDROID_ANALYTICS.read_text(encoding="utf-8")

        android_screens = _extract_string_constants(
            _extract_block(android_source, "object AnalyticsScreens"),
            r'const val \w+\s*=\s*"([^"]+)"',
        )
        self.assertEqual({"Home", "Protection"}, android_screens)

    def test_product_specific_events_replace_random_timer_events(self):
        source = ANDROID_ANALYTICS.read_text(encoding="utf-8")
        for event in [
            "call_screening_enabled",
            "call_screening_status_refreshed",
            "call_screened",
            "spam_call_blocked",
            "spam_call_silenced",
            "first_protection_enabled",
            "first_spam_blocked",
        ]:
            self.assertIn(event, source)
        for legacy_event in ["timer_started", "timer_completed", "timer_abandoned"]:
            self.assertNotIn(legacy_event, source)

    def test_app_surfaces_emit_answer_guard_analytics(self):
        android_source = ANDROID_MAIN.read_text(encoding="utf-8")
        ios_source = IOS_HOME_SCREEN.read_text(encoding="utf-8")

        self.assertIn("AnalyticsEvents.CALL_SCREENING_ENABLED", android_source)
        self.assertIn("trackFirstProtectionEnabledIfNeeded()", android_source)
        self.assertIn("Spam & Scam Call Protection", ios_source)
        self.assertIn("Call Screening", ios_source)

    def test_result_property_is_defined_on_both_platforms(self):
        android_source = ANDROID_ANALYTICS.read_text(encoding="utf-8")
        ios_source = IOS_ANALYTICS.read_text(encoding="utf-8")
        self.assertIn('const val RESULT = "result"', android_source)
        self.assertIn('static let result = "result"', ios_source)

    def test_lifecycle_autocapture_disabled_on_both_platforms(self):
        android_source = ANDROID_ANALYTICS.read_text(encoding="utf-8")
        ios_source = IOS_ANALYTICS.read_text(encoding="utf-8")
        self.assertIn("captureApplicationLifecycleEvents = false", android_source)
        self.assertIn("config.captureApplicationLifecycleEvents = false", ios_source)

    def test_manual_lifecycle_events_tracked_on_initialize(self):
        android_source = ANDROID_ANALYTICS.read_text(encoding="utf-8")
        ios_source = IOS_ANALYTICS.read_text(encoding="utf-8")
        self.assertIn("trackApplicationLifecycleEvents()", android_source)
        self.assertIn("trackApplicationLifecycleEvents()", ios_source)
        self.assertIn('const val APPLICATION_INSTALLED = "Application Installed"', android_source)
        self.assertIn('const val APPLICATION_OPENED = "Application Opened"', android_source)
        self.assertIn('static let applicationInstalled = "Application Installed"', ios_source)
        self.assertIn('static let applicationOpened = "Application Opened"', ios_source)


if __name__ == "__main__":
    unittest.main()

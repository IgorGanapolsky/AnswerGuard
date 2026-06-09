package com.igorganapolsky.answerguard.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import com.igorganapolsky.answerguard.billing.ProManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Unit tests for [PaywallSheet] running on Robolectric.
 *
 * Notes:
 * - [androidx.compose.material3.ModalBottomSheet] hosts content in a Dialog window,
 *   but `createComposeRule()` still merges its semantics tree so we can locate the
 *   text/button nodes via their visible labels.
 * - The hidden-unlock long-press requires an 8s hold in production; we exercise
 *   the modifier in isolation with a short duration to keep the test fast.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PaywallSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class RecordingUriHandler : UriHandler {
        val opened = mutableListOf<String>()
        override fun openUri(uri: String) {
            opened += uri
        }
    }

    private fun setPaywall(
        onPurchase: (String) -> Unit = {},
        onRestore: () -> Unit = {},
        onDismiss: () -> Unit = {},
        onSecretUnlock: (() -> Unit)? = null,
        uriHandler: UriHandler = RecordingUriHandler(),
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                MaterialTheme {
                    PaywallSheet(
                        entryPoint = "unit-test",
                        familyPrice = "$29.99/yr",
                        businessPrice = "$49.99/yr",
                        onPurchase = onPurchase,
                        onRestore = onRestore,
                        onDismiss = onDismiss,
                        onSecretUnlock = onSecretUnlock,
                    )
                }
            }
        }
    }

    @Test
    fun renders_headline_and_subheadline() {
        setPaywall()
        composeTestRule.onNodeWithText(PAYWALL_HEADLINE).assertExists()
        composeTestRule.onNodeWithText(PAYWALL_SUBHEADLINE).assertExists()
    }

    @Test
    fun renders_pricing_footer() {
        setPaywall()
        composeTestRule.onNodeWithText(PAYWALL_PRICING_FOOTER).assertExists()
    }

    @Test
    fun renders_all_feature_rows() {
        setPaywall()
        // Available today — features actually shipped in v1.2.x
        composeTestRule.onNodeWithText("Spam-screening controls").assertExists()
        composeTestRule.onNodeWithText("Google Play family sharing").assertExists()
        composeTestRule.onNodeWithText("Business priority support").assertExists()
        // Coming soon — clearly demarcated as not yet shipped (Play DBA policy compliance)
        composeTestRule.onNodeWithText("On-device AI intent analysis").assertExists()
        composeTestRule.onNodeWithText("Voice deepfake defense").assertExists()
    }

    @Test
    fun separates_shipped_features_from_roadmap() {
        setPaywall()
        composeTestRule.onNodeWithText("Available today").assertExists()
        composeTestRule.onNodeWithText("Coming soon (not yet shipped)").assertExists()
    }

    @Test
    fun renders_plan_comparison_card_entries() {
        setPaywall()
        composeTestRule.onNodeWithText("Which plan is right for you?").assertExists()
        composeTestRule.onNodeWithText("Family Protection (\$29.99/yr)").assertExists()
        composeTestRule.onNodeWithText("Business Plan (\$49.99/yr)").assertExists()
    }

    @Test
    fun renders_family_and_business_buttons_with_prices() {
        setPaywall()
        composeTestRule.onNodeWithText("Family Protection Plan • \$29.99/yr").assertExists()
        composeTestRule.onNodeWithText("Business Shield Plan • \$49.99/yr").assertExists()
    }

    @Test
    fun renders_restore_and_dismiss_buttons() {
        setPaywall()
        composeTestRule.onNodeWithText("Restore purchase").assertExists()
        composeTestRule.onNodeWithText("Not now").assertExists()
    }

    @Test
    fun family_button_click_invokes_onPurchase_with_elite_product_id() {
        val purchased = mutableListOf<String>()
        setPaywall(onPurchase = { purchased += it })
        composeTestRule.onNodeWithText("Family Protection Plan • \$29.99/yr").performClick()
        assertEquals(listOf(ProManager.ELITE_PRODUCT_ID), purchased)
    }

    @Test
    fun business_button_click_invokes_onPurchase_with_business_product_id() {
        val purchased = mutableListOf<String>()
        setPaywall(onPurchase = { purchased += it })
        composeTestRule.onNodeWithText("Business Shield Plan • \$49.99/yr").performClick()
        assertEquals(listOf(ProManager.BUSINESS_PRODUCT_ID), purchased)
    }

    @Test
    fun restore_button_click_invokes_onRestore() {
        var restoreCount = 0
        setPaywall(onRestore = { restoreCount += 1 })
        composeTestRule.onNodeWithText("Restore purchase").performClick()
        assertEquals(1, restoreCount)
    }

    @Test
    fun not_now_button_click_invokes_onDismiss() {
        var dismissCount = 0
        setPaywall(onDismiss = { dismissCount += 1 })
        composeTestRule.onNodeWithText("Not now").performClick()
        // ModalBottomSheet's own onDismissRequest can also fire when the sheet is
        // recomposed; assert at least one dismiss happened from clicking "Not now".
        assertTrue("expected onDismiss to fire at least once, was $dismissCount", dismissCount >= 1)
    }

    @Test
    fun web_checkout_link_opens_upgrade_uri() {
        val uri = RecordingUriHandler()
        setPaywall(uriHandler = uri)
        composeTestRule.onNodeWithText("Direct Support (Web Checkout)").performClick()
        assertTrue(
            "expected upgrade URI to be opened, got ${uri.opened}",
            uri.opened.any { it.contains("/upgrade/") },
        )
    }

    @Test
    fun privacy_link_opens_privacy_uri() {
        val uri = RecordingUriHandler()
        setPaywall(uriHandler = uri)
        composeTestRule.onNodeWithText("Privacy Policy").performScrollTo().performClick()
        assertTrue(
            "expected privacy URI to be opened, got ${uri.opened}",
            uri.opened.any { it.contains("privacy-policy") },
        )
    }

    @Test
    fun terms_link_opens_eula_uri() {
        val uri = RecordingUriHandler()
        setPaywall(uriHandler = uri)
        composeTestRule.onNodeWithText("Terms of Use").performScrollTo().performClick()
        assertTrue(
            "expected eula URI to be opened, got ${uri.opened}",
            uri.opened.any { it.contains("eula") },
        )
    }

    @Test
    fun secret_unlock_callback_provided_does_not_crash_rendering() {
        // Ensures the conditional Modifier branch (holdForHiddenUnlock attached)
        // composes without error when onSecretUnlock is non-null.
        setPaywall(onSecretUnlock = { /* no-op */ })
        composeTestRule.onNodeWithText(PAYWALL_HEADLINE).assertExists()
    }

    // -------- holdForHiddenUnlock modifier tests --------

    private class CountingHaptic : HapticFeedback {
        var count = 0
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            count += 1
        }
    }

    @Test
    fun holdForHiddenUnlock_fires_callback_after_hold_duration() {
        val haptic = CountingHaptic()
        var fired = 0
        val holdMs = 200L

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .holdForHiddenUnlock(
                            holdDurationMs = holdMs,
                            haptic = haptic,
                            onHoldComplete = { fired += 1 },
                        )
                ) {
                    Text("hold-target")
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(50)

        val target = composeTestRule.onNodeWithText("hold-target")
        target.performTouchInput { down(center) }

        // Advance well past the hold duration to trigger the timeout path.
        composeTestRule.mainClock.advanceTimeBy(holdMs * 4)
        composeTestRule.waitForIdle()

        target.performTouchInput { up() }
        composeTestRule.mainClock.advanceTimeBy(50)
        composeTestRule.waitForIdle()

        assertEquals("onHoldComplete should fire exactly once", 1, fired)
        assertEquals("haptic feedback should fire exactly once", 1, haptic.count)
    }

    @Test
    fun holdForHiddenUnlock_does_not_fire_on_quick_tap() {
        val haptic = CountingHaptic()
        var fired = 0
        val holdMs = 500L

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .holdForHiddenUnlock(
                            holdDurationMs = holdMs,
                            haptic = haptic,
                            onHoldComplete = { fired += 1 },
                        )
                ) {
                    Text("tap-target")
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(50)

        val target = composeTestRule.onNodeWithText("tap-target")
        target.performTouchInput { down(center) }
        composeTestRule.mainClock.advanceTimeBy(holdMs / 5) // release early
        target.performTouchInput { up() }
        composeTestRule.mainClock.advanceTimeBy(holdMs * 2)
        composeTestRule.waitForIdle()

        assertEquals("onHoldComplete must not fire on a quick tap", 0, fired)
        assertEquals("haptic feedback must not fire on a quick tap", 0, haptic.count)
    }
}

package com.igorganapolsky.answerguard.analytics

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [AnalyticsService].
 *
 * - Uses [RobolectricTestRunner] so [android.os.Build] static fields are populated
 *   (the production [AnalyticsService.isEmulator] reads `Build.FINGERPRINT` etc.
 *   directly; without a runtime they would NPE).
 * - PostHog SDK companion-object methods (`PostHog.capture`, `PostHog.identify`,
 *   `PostHog.reset`, `PostHog.flush`, `PostHog.screen`, `PostHog.distinctId`) are
 *   mocked via `mockkObject(PostHog.Companion)`. Similarly
 *   `PostHogAndroid.setup` is intercepted via `mockkObject(PostHogAndroid.Companion)`
 *   so no real PostHog client is created.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AnalyticsServiceTest {
    private lateinit var application: Application
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    private lateinit var service: AnalyticsService

    @Before
    fun setUp() {
        application = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)

        every { application.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.apply() } just Runs

        // Default: prefs returns "false"/"null" so initialize() exercises the
        // first-install and first-open branches.
        every { prefs.getBoolean(any(), any()) } returns false
        every { prefs.getString(any(), any()) } returns null

        // Intercept PostHog companion-object calls so no real client work happens.
        mockkObject(PostHog.Companion)
        mockkObject(PostHogAndroid.Companion)
        every { PostHog.capture(any(), any(), any(), any(), any(), any()) } just Runs
        every { PostHog.identify(any(), any(), any()) } just Runs
        every { PostHog.screen(any(), any()) } just Runs
        every { PostHog.reset() } just Runs
        every { PostHog.flush() } just Runs
        every { PostHog.distinctId() } returns "distinct-123"
        every { PostHogAndroid.setup(any(), any<PostHogAndroidConfig>()) } just Runs

        service = AnalyticsService()
    }

    @After
    fun tearDown() {
        unmockkObject(PostHog.Companion)
        unmockkObject(PostHogAndroid.Companion)
    }

    // ---------------- initialize ----------------

    @Test
    fun `initialize wires up PostHog and emits lifecycle plus first-open events`() {
        service.initialize(application, "test-posthog-key")

        verify(exactly = 1) {
            PostHogAndroid.setup(application, any<PostHogAndroidConfig>())
        }
        verify(atLeast = 1) { PostHog.identify(any(), any(), any()) }
        // APPLICATION_OPENED + APPLICATION_INSTALLED + FIRST_OPEN
        verify(atLeast = 3) { PostHog.capture(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `initialize is idempotent`() {
        service.initialize(application, "test-posthog-key")
        service.initialize(application, "test-posthog-key")
        service.initialize(application, "test-posthog-key")

        verify(exactly = 1) {
            PostHogAndroid.setup(application, any<PostHogAndroidConfig>())
        }
    }

    @Test
    fun `initialize persists generated distinct id when none stored`() {
        service.initialize(application, "test-posthog-key")

        verify { editor.putString(eq("posthog_distinct_id"), any()) }
    }

    @Test
    fun `initialize reuses existing distinct id without regenerating`() {
        every { prefs.getString("posthog_distinct_id", null) } returns "existing-id"

        service.initialize(application, "test-posthog-key")

        verify { PostHog.identify(eq("existing-id"), any(), any()) }
        verify(exactly = 0) { editor.putString(eq("posthog_distinct_id"), any()) }
    }

    @Test
    fun `initialize skips APPLICATION_INSTALLED and FIRST_OPEN when already tracked`() {
        every { prefs.getBoolean("has_tracked_application_installed", false) } returns true
        every { prefs.getBoolean("has_first_opened", false) } returns true

        service.initialize(application, "test-posthog-key")

        verify(exactly = 0) {
            PostHog.capture(eq(AnalyticsEvents.APPLICATION_INSTALLED), any(), any(), any(), any(), any())
        }
        verify(exactly = 0) {
            PostHog.capture(eq(AnalyticsEvents.FIRST_OPEN), any(), any(), any(), any(), any())
        }
        verify(atLeast = 1) {
            PostHog.capture(eq(AnalyticsEvents.APPLICATION_OPENED), any(), any(), any(), any(), any())
        }
    }

    // ---------------- !initialized early-return paths ----------------

    @Test
    fun `track is a no-op before initialize`() {
        service.track("any_event", mapOf("k" to "v"))

        verify(exactly = 0) { PostHog.capture(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `screen is a no-op before initialize`() {
        service.screen(AnalyticsScreens.HOME, null)

        verify(exactly = 0) { PostHog.screen(any(), any()) }
    }

    @Test
    fun `identify is a no-op before initialize`() {
        service.identify("user-1", null)

        verify(exactly = 0) { PostHog.identify(any(), any(), any()) }
    }

    @Test
    fun `reset is a no-op before initialize`() {
        service.reset()

        verify(exactly = 0) { PostHog.reset() }
    }

    @Test
    fun `flush is a no-op before initialize`() {
        service.flush()

        verify(exactly = 0) { PostHog.flush() }
    }

    @Test
    fun `trackDeepLink is a no-op before initialize`() {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.getQueryParameter(any()) } returns "x"
        every { uri.path } returns "/p"

        service.trackDeepLink(uri)

        verify(exactly = 0) { PostHog.capture(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `trackFirstProtectionEnabledIfNeeded is a no-op before initialize`() {
        service.trackFirstProtectionEnabledIfNeeded()

        verify(exactly = 0) { PostHog.capture(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { editor.putBoolean("has_first_configured", any()) }
    }

    @Test
    fun `trackFirstSpamBlockedIfNeeded is a no-op before initialize`() {
        service.trackFirstSpamBlockedIfNeeded()

        verify(exactly = 0) { PostHog.capture(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { editor.putBoolean("has_first_completed", any()) }
    }

    // ---------------- happy-path forwarding to PostHog ----------------

    @Test
    fun `track forwards event with merged properties to PostHog`() {
        service.initialize(application, "test-posthog-key")

        service.track("custom_event", mapOf("custom_key" to "custom_value"))

        verify(atLeast = 1) {
            PostHog.capture(
                eq("custom_event"),
                any(),
                match<Map<String, Any>> { props ->
                    props["custom_key"] == "custom_value" &&
                        props["platform"] == "android"
                },
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `track with null properties still includes context properties`() {
        service.initialize(application, "test-posthog-key")

        service.track("plain_event", null)

        verify(atLeast = 1) {
            PostHog.capture(
                eq("plain_event"),
                any(),
                match<Map<String, Any>> { props -> props["platform"] == "android" },
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `screen forwards screen name with merged properties to PostHog`() {
        service.initialize(application, "test-posthog-key")

        service.screen(AnalyticsScreens.HOME, mapOf("entry_point" to "deeplink"))

        verify {
            PostHog.screen(
                eq(AnalyticsScreens.HOME),
                match<Map<String, Any>> { props ->
                    props["entry_point"] == "deeplink" &&
                        props["platform"] == "android"
                },
            )
        }
    }

    @Test
    fun `identify forwards user id and merged properties to PostHog`() {
        service.initialize(application, "test-posthog-key")

        service.identify("user-42", mapOf("plan" to "pro"))

        verify {
            PostHog.identify(
                eq("user-42"),
                match<Map<String, Any>> { props ->
                    props["plan"] == "pro" &&
                        props["platform"] == "android"
                },
                any(),
            )
        }
    }

    @Test
    fun `screen with default null properties still forwards to PostHog`() {
        service.initialize(application, "test-posthog-key")

        // Call without the properties argument to exercise the kotlin-generated
        // `screen$default` bridge.
        service.screen(AnalyticsScreens.PROTECTION)

        verify { PostHog.screen(eq(AnalyticsScreens.PROTECTION), any()) }
    }

    @Test
    fun `identify with default null properties still forwards to PostHog`() {
        service.initialize(application, "test-posthog-key")

        // Call without the properties argument to exercise the kotlin-generated
        // `identify$default` bridge.
        service.identify("user-default")

        verify { PostHog.identify(eq("user-default"), any(), any()) }
    }

    @Test
    fun `reset forwards to PostHog`() {
        service.initialize(application, "test-posthog-key")

        service.reset()

        verify { PostHog.reset() }
    }

    @Test
    fun `flush forwards to PostHog`() {
        service.initialize(application, "test-posthog-key")

        service.flush()

        verify { PostHog.flush() }
    }

    @Test
    fun `trackFirstProtectionEnabledIfNeeded tracks once and persists`() {
        service.initialize(application, "test-posthog-key")
        every { prefs.getBoolean("has_first_configured", false) } returns false

        service.trackFirstProtectionEnabledIfNeeded()

        verify {
            PostHog.capture(
                eq(AnalyticsEvents.FIRST_PROTECTION_ENABLED),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
        verify { editor.putBoolean("has_first_configured", true) }
    }

    @Test
    fun `trackFirstProtectionEnabledIfNeeded does nothing when already tracked`() {
        service.initialize(application, "test-posthog-key")
        every { prefs.getBoolean("has_first_configured", false) } returns true

        service.trackFirstProtectionEnabledIfNeeded()

        verify(exactly = 0) {
            PostHog.capture(
                eq(AnalyticsEvents.FIRST_PROTECTION_ENABLED),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `trackFirstSpamBlockedIfNeeded tracks once and persists`() {
        service.initialize(application, "test-posthog-key")
        every { prefs.getBoolean("has_first_completed", false) } returns false

        service.trackFirstSpamBlockedIfNeeded()

        verify {
            PostHog.capture(
                eq(AnalyticsEvents.FIRST_SPAM_BLOCKED),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
        verify { editor.putBoolean("has_first_completed", true) }
    }

    @Test
    fun `trackFirstSpamBlockedIfNeeded does nothing when already tracked`() {
        service.initialize(application, "test-posthog-key")
        every { prefs.getBoolean("has_first_completed", false) } returns true

        service.trackFirstSpamBlockedIfNeeded()

        verify(exactly = 0) {
            PostHog.capture(
                eq(AnalyticsEvents.FIRST_SPAM_BLOCKED),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    // ---------------- trackDeepLink (UTM extraction) ----------------

    @Test
    fun `trackDeepLink with valid utm params persists and emits deep_link_opened`() {
        service.initialize(application, "test-posthog-key")

        val uri = Uri.parse(
            "https://answerguard.app/install?utm_source=twitter" +
                "&utm_medium=cpc" +
                "&utm_campaign=launch" +
                "&utm_content=hero" +
                "&utm_term=spam-blocker",
        )

        service.trackDeepLink(uri)

        verify { editor.putString("utm_source", "twitter") }
        verify { editor.putString("utm_medium", "cpc") }
        verify { editor.putString("utm_campaign", "launch") }
        verify { editor.putString("utm_content", "hero") }
        verify { editor.putString("utm_term", "spam-blocker") }

        verify {
            PostHog.identify(
                eq("distinct-123"),
                match<Map<String, Any>> { props -> props["utm_source"] == "twitter" },
                any(),
            )
        }
        verify {
            PostHog.capture(
                eq(AnalyticsEvents.DEEP_LINK_OPENED),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `trackDeepLink with referring path only still emits attribution event`() {
        service.initialize(application, "test-posthog-key")

        val uri = Uri.parse("https://answerguard.app/promo/special")

        service.trackDeepLink(uri)

        verify {
            PostHog.capture(
                eq(AnalyticsEvents.DEEP_LINK_OPENED),
                any(),
                match<Map<String, Any>> { props -> props["referring_path"] == "/promo/special" },
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `trackDeepLink with no utm params and no path is a no-op`() {
        service.initialize(application, "test-posthog-key")

        // Hierarchical URI with no query params and no path, so the UTM extractor
        // returns an empty map and no DEEP_LINK_OPENED event is emitted.
        val uri = Uri.parse("https://answerguard.app")

        service.trackDeepLink(uri)

        verify(exactly = 0) {
            PostHog.capture(
                eq(AnalyticsEvents.DEEP_LINK_OPENED),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `trackDeepLink ignores blank utm values`() {
        service.initialize(application, "test-posthog-key")

        val uri = Uri.parse("https://answerguard.app/?utm_source=&utm_medium=email")

        service.trackDeepLink(uri)

        verify(exactly = 0) { editor.putString("utm_source", any()) }
        verify { editor.putString("utm_medium", "email") }
    }

    // ---------------- getStoredAttribution ----------------

    @Test
    fun `getStoredAttribution returns persisted utm keys`() {
        every { prefs.getString("utm_source", null) } returns "google"
        every { prefs.getString("utm_medium", null) } returns "cpc"
        every { prefs.getString("utm_campaign", null) } returns null
        every { prefs.getString("utm_content", null) } returns null
        every { prefs.getString("utm_term", null) } returns null

        service.initialize(application, "test-posthog-key")

        val stored = service.getStoredAttribution()

        assertThat(stored).containsExactly("utm_source", "google", "utm_medium", "cpc")
    }

    @Test
    fun `getStoredAttribution returns empty map when prefs are null`() {
        // Service has not been initialized -> prefs field is null -> empty map.
        val stored = service.getStoredAttribution()

        assertThat(stored).isEmpty()
    }

    // ---------------- AnalyticsEvents / Properties / Screens constants ----------------

    @Test
    fun `AnalyticsEvents constants are stable wire names`() {
        assertThat(AnalyticsEvents.APPLICATION_INSTALLED).isEqualTo("Application Installed")
        assertThat(AnalyticsEvents.APPLICATION_OPENED).isEqualTo("Application Opened")
        assertThat(AnalyticsEvents.CALL_SCREENING_ENABLED).isEqualTo("call_screening_enabled")
        assertThat(AnalyticsEvents.CALL_SCREENING_STATUS_REFRESHED)
            .isEqualTo("call_screening_status_refreshed")
        assertThat(AnalyticsEvents.CALL_SCREENED).isEqualTo("call_screened")
        assertThat(AnalyticsEvents.SPAM_CALL_BLOCKED).isEqualTo("spam_call_blocked")
        assertThat(AnalyticsEvents.SPAM_CALL_SILENCED).isEqualTo("spam_call_silenced")
        assertThat(AnalyticsEvents.SETTINGS_CHANGED).isEqualTo("settings_changed")
        assertThat(AnalyticsEvents.REVIEW_PROMPT_REQUESTED).isEqualTo("review_prompt_requested")
        assertThat(AnalyticsEvents.WRITE_REVIEW_TAPPED).isEqualTo("write_review_tapped")
        assertThat(AnalyticsEvents.PAYWALL_VIEWED).isEqualTo("paywall_viewed")
        assertThat(AnalyticsEvents.PAYWALL_DISMISSED).isEqualTo("paywall_dismissed")
        assertThat(AnalyticsEvents.PAYWALL_PURCHASE_ATTEMPT).isEqualTo("paywall_purchase_attempt")
        assertThat(AnalyticsEvents.PAYWALL_PURCHASE_SUCCESS).isEqualTo("paywall_purchase_success")
        assertThat(AnalyticsEvents.PAYWALL_PURCHASE_RESULT).isEqualTo("paywall_purchase_result")
        assertThat(AnalyticsEvents.PAYWALL_RESTORE_RESULT).isEqualTo("paywall_restore_result")
        assertThat(AnalyticsEvents.DEEP_LINK_OPENED).isEqualTo("deep_link_opened")
        assertThat(AnalyticsEvents.APPLE_ADS_ATTRIBUTION).isEqualTo("apple_ads_attribution")
        assertThat(AnalyticsEvents.FIRST_OPEN).isEqualTo("first_open")
        assertThat(AnalyticsEvents.FIRST_PROTECTION_ENABLED).isEqualTo("first_protection_enabled")
        assertThat(AnalyticsEvents.FIRST_SPAM_BLOCKED).isEqualTo("first_spam_blocked")
    }

    @Test
    fun `AnalyticsProperties constants match server schema`() {
        assertThat(AnalyticsProperties.ENTRY_POINT).isEqualTo("entry_point")
        assertThat(AnalyticsProperties.RESULT).isEqualTo("result")
        assertThat(AnalyticsProperties.SUCCESS).isEqualTo("success")
        assertThat(AnalyticsProperties.RESPONSE_CODE).isEqualTo("response_code")
        assertThat(AnalyticsProperties.DEBUG_MESSAGE).isEqualTo("debug_message")
        assertThat(AnalyticsProperties.SOURCE).isEqualTo("source")
        assertThat(AnalyticsProperties.PRODUCT_ID).isEqualTo("product_id")
        assertThat(AnalyticsProperties.ENTITLEMENT_LEVEL).isEqualTo("entitlement_level")
        assertThat(AnalyticsProperties.ENVIRONMENT).isEqualTo("environment")
        assertThat(AnalyticsProperties.BUILD_AUDIENCE).isEqualTo("build_audience")
        assertThat(AnalyticsProperties.BUILD_TYPE).isEqualTo("build_type")
        assertThat(AnalyticsProperties.RUNTIME_TARGET).isEqualTo("runtime_target")
    }

    @Test
    fun `AnalyticsScreens constants are stable identifiers`() {
        assertThat(AnalyticsScreens.HOME).isEqualTo("Home")
        assertThat(AnalyticsScreens.PROTECTION).isEqualTo("Protection")
    }

    // ---------------- context properties shape ----------------

    @Test
    fun `initialize publishes build context properties to identify`() {
        service.initialize(application, "test-posthog-key")

        verify(atLeast = 1) {
            PostHog.identify(
                any(),
                match<Map<String, Any>> { props ->
                    props["platform"] == "android" &&
                        props.containsKey(AnalyticsProperties.ENVIRONMENT) &&
                        props.containsKey(AnalyticsProperties.BUILD_AUDIENCE) &&
                        props.containsKey(AnalyticsProperties.BUILD_TYPE) &&
                        props.containsKey(AnalyticsProperties.RUNTIME_TARGET)
                },
                any(),
            )
        }
    }
}

package com.igorganapolsky.answerguard.analytics

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.igorganapolsky.answerguard.billing.MonetizationAnalyticsPayload
import com.posthog.android.PostHogAndroid
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals

class AnalyticsServiceTest {

    private lateinit var mockApplication: Application
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    private lateinit var analyticsService: AnalyticsService

    @Before
    fun setUp() {
        mockApplication = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)

        every { mockApplication.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor

        mockkObject(PostHogAndroid.Companion)
        every { PostHogAndroid.setup(any(), any()) } returns Unit

        analyticsService = AnalyticsService()
    }

    @After
    fun tearDown() {
        unmockkObject(PostHogAndroid.Companion)
    }

    @Test
    fun `initialize without api key does not crash`() {
        analyticsService.initialize(mockApplication)
    }

    @Test
    fun `track methods do not crash if uninitialized`() {
        analyticsService.track("test")
        analyticsService.screen("test_screen")
        analyticsService.identify("user1")
        analyticsService.reset()
        analyticsService.flush()
        analyticsService.trackDeepLink(mockk<Uri>(relaxed = true))
        analyticsService.trackFirstProtectionEnabledIfNeeded()
        analyticsService.trackFirstSpamBlockedIfNeeded()
    }

    @Test
    fun `AnalyticsPayload properties map correctly`() {
        val attempt = MonetizationAnalyticsPayload.attemptProperties("paywall", "button", "pro")
        assertEquals("paywall", attempt[AnalyticsProperties.SOURCE])
        assertEquals("button", attempt[AnalyticsProperties.ENTRY_POINT])
        assertEquals("pro", attempt[AnalyticsProperties.PRODUCT_ID])
    }
}

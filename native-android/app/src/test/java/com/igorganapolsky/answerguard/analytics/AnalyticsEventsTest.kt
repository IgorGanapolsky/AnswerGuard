package com.igorganapolsky.answerguard.analytics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnalyticsEventsTest {
    @Test
    fun `lifecycle event names match server contract`() {
        assertThat(AnalyticsEvents.APPLICATION_INSTALLED).isEqualTo("Application Installed")
        assertThat(AnalyticsEvents.APPLICATION_OPENED).isEqualTo("Application Opened")
    }

    @Test
    fun `screening event names use snake case wire format`() {
        assertThat(AnalyticsEvents.CALL_SCREENING_ENABLED).isEqualTo("call_screening_enabled")
        assertThat(AnalyticsEvents.CALL_SCREENING_STATUS_REFRESHED)
            .isEqualTo("call_screening_status_refreshed")
        assertThat(AnalyticsEvents.CALL_SCREENED).isEqualTo("call_screened")
        assertThat(AnalyticsEvents.SPAM_CALL_BLOCKED).isEqualTo("spam_call_blocked")
        assertThat(AnalyticsEvents.SPAM_CALL_SILENCED).isEqualTo("spam_call_silenced")
    }

    @Test
    fun `paywall purchase event names are stable`() {
        assertThat(AnalyticsEvents.PAYWALL_VIEWED).isEqualTo("paywall_viewed")
        assertThat(AnalyticsEvents.PAYWALL_DISMISSED).isEqualTo("paywall_dismissed")
        assertThat(AnalyticsEvents.PAYWALL_PURCHASE_ATTEMPT).isEqualTo("paywall_purchase_attempt")
        assertThat(AnalyticsEvents.PAYWALL_PURCHASE_SUCCESS).isEqualTo("paywall_purchase_success")
        assertThat(AnalyticsEvents.PAYWALL_PURCHASE_RESULT).isEqualTo("paywall_purchase_result")
        assertThat(AnalyticsEvents.PAYWALL_RESTORE_RESULT).isEqualTo("paywall_restore_result")
    }

    @Test
    fun `attribution event names are stable`() {
        assertThat(AnalyticsEvents.DEEP_LINK_OPENED).isEqualTo("deep_link_opened")
        assertThat(AnalyticsEvents.APPLE_ADS_ATTRIBUTION).isEqualTo("apple_ads_attribution")
    }

    @Test
    fun `onboarding funnel event names are stable`() {
        assertThat(AnalyticsEvents.FIRST_OPEN).isEqualTo("first_open")
        assertThat(AnalyticsEvents.FIRST_PROTECTION_ENABLED).isEqualTo("first_protection_enabled")
        assertThat(AnalyticsEvents.FIRST_SPAM_BLOCKED).isEqualTo("first_spam_blocked")
    }

    @Test
    fun `settings and review event names are stable`() {
        assertThat(AnalyticsEvents.SETTINGS_CHANGED).isEqualTo("settings_changed")
        assertThat(AnalyticsEvents.REVIEW_PROMPT_REQUESTED).isEqualTo("review_prompt_requested")
        assertThat(AnalyticsEvents.WRITE_REVIEW_TAPPED).isEqualTo("write_review_tapped")
    }
}

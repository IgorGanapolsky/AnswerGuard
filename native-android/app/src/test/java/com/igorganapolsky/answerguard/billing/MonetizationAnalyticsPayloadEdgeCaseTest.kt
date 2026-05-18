package com.igorganapolsky.answerguard.billing

import com.google.common.truth.Truth.assertThat
import com.igorganapolsky.answerguard.analytics.AnalyticsProperties
import org.junit.Test

class MonetizationAnalyticsPayloadEdgeCaseTest {
    @Test
    fun `attemptProperties falls back entry point to source when null`() {
        val properties =
            MonetizationAnalyticsPayload.attemptProperties(
                source = MonetizationSources.AUTO_RESTORE,
                entryPoint = null,
                productID = ProManager.BASE_PRODUCT_ID,
            )

        assertThat(properties[AnalyticsProperties.ENTRY_POINT]).isEqualTo(MonetizationSources.AUTO_RESTORE)
        assertThat(properties[AnalyticsProperties.SOURCE]).isEqualTo(MonetizationSources.AUTO_RESTORE)
        assertThat(properties[AnalyticsProperties.PRODUCT_ID]).isEqualTo(ProManager.BASE_PRODUCT_ID)
    }

    @Test
    fun `attemptProperties contains exactly three keys`() {
        val properties =
            MonetizationAnalyticsPayload.attemptProperties(
                source = MonetizationSources.PAYWALL,
                entryPoint = "home_pro_card",
                productID = ProManager.ELITE_PRODUCT_ID,
            )

        assertThat(properties.keys).containsExactly(
            AnalyticsProperties.SOURCE,
            AnalyticsProperties.ENTRY_POINT,
            AnalyticsProperties.PRODUCT_ID,
        )
    }

    @Test
    fun `successProperties omits product id when null`() {
        val properties =
            MonetizationAnalyticsPayload.successProperties(
                source = MonetizationSources.PAYWALL,
                entryPoint = "home_pro_card",
                productID = null,
                responseCode = 0,
                debugMessage = "ok",
            )

        assertThat(properties).doesNotContainKey(AnalyticsProperties.PRODUCT_ID)
        assertThat(properties[AnalyticsProperties.SUCCESS]).isEqualTo(true)
    }

    @Test
    fun `successProperties defaults debug message to empty when null`() {
        val properties =
            MonetizationAnalyticsPayload.successProperties(
                source = MonetizationSources.PAYWALL,
                entryPoint = "home_pro_card",
                productID = ProManager.ELITE_PRODUCT_ID,
                responseCode = 0,
                debugMessage = null,
            )

        assertThat(properties[AnalyticsProperties.DEBUG_MESSAGE]).isEqualTo("")
    }

    @Test
    fun `successProperties always marks success true regardless of response code`() {
        val properties =
            MonetizationAnalyticsPayload.successProperties(
                source = MonetizationSources.BILLING_CALLBACK,
                entryPoint = null,
                productID = ProManager.BASE_PRODUCT_ID,
                responseCode = 5,
                debugMessage = "weird",
            )

        assertThat(properties[AnalyticsProperties.SUCCESS]).isEqualTo(true)
        assertThat(properties[AnalyticsProperties.RESPONSE_CODE]).isEqualTo(5)
    }

    @Test
    fun `resultProperties carries explicit response code and source verbatim`() {
        val properties =
            MonetizationAnalyticsPayload.resultProperties(
                success = false,
                result = "cancelled",
                source = MonetizationSources.PAYWALL,
                entryPoint = "home_pro_card",
                responseCode = 1,
                debugMessage = "user_cancelled",
            )

        assertThat(properties[AnalyticsProperties.RESPONSE_CODE]).isEqualTo(1)
        assertThat(properties[AnalyticsProperties.SUCCESS]).isEqualTo(false)
        assertThat(properties[AnalyticsProperties.SOURCE]).isEqualTo(MonetizationSources.PAYWALL)
        assertThat(properties[AnalyticsProperties.RESULT]).isEqualTo("cancelled")
        assertThat(properties[AnalyticsProperties.DEBUG_MESSAGE]).isEqualTo("user_cancelled")
    }

    @Test
    fun `resultProperties exposes exactly six keys`() {
        val properties =
            MonetizationAnalyticsPayload.resultProperties(
                success = true,
                result = "success",
                source = MonetizationSources.PAYWALL,
                entryPoint = "home_pro_card",
                responseCode = 0,
                debugMessage = "ok",
            )

        assertThat(properties.keys).containsExactly(
            AnalyticsProperties.RESULT,
            AnalyticsProperties.SUCCESS,
            AnalyticsProperties.SOURCE,
            AnalyticsProperties.ENTRY_POINT,
            AnalyticsProperties.RESPONSE_CODE,
            AnalyticsProperties.DEBUG_MESSAGE,
        )
    }

    @Test
    fun `resultProperties uses blank entry point as fallback to source`() {
        // entryPoint = "" is not null but elvis operator only fires on null; explicit empty stays as-is
        val properties =
            MonetizationAnalyticsPayload.resultProperties(
                success = true,
                result = "restored",
                source = MonetizationSources.AUTO_RESTORE,
                entryPoint = "",
                responseCode = 0,
                debugMessage = null,
            )

        assertThat(properties[AnalyticsProperties.ENTRY_POINT]).isEqualTo("")
    }
}

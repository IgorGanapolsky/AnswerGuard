package com.igorganapolsky.answerguard.analytics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnalyticsPropertiesTest {
    @Test
    fun `core property names use snake case`() {
        assertThat(AnalyticsProperties.ENTRY_POINT).isEqualTo("entry_point")
        assertThat(AnalyticsProperties.RESULT).isEqualTo("result")
        assertThat(AnalyticsProperties.SUCCESS).isEqualTo("success")
        assertThat(AnalyticsProperties.RESPONSE_CODE).isEqualTo("response_code")
        assertThat(AnalyticsProperties.DEBUG_MESSAGE).isEqualTo("debug_message")
        assertThat(AnalyticsProperties.SOURCE).isEqualTo("source")
    }

    @Test
    fun `product and entitlement property names match server schema`() {
        assertThat(AnalyticsProperties.PRODUCT_ID).isEqualTo("product_id")
        assertThat(AnalyticsProperties.ENTITLEMENT_LEVEL).isEqualTo("entitlement_level")
    }

    @Test
    fun `build context property names match server schema`() {
        assertThat(AnalyticsProperties.ENVIRONMENT).isEqualTo("environment")
        assertThat(AnalyticsProperties.BUILD_AUDIENCE).isEqualTo("build_audience")
        assertThat(AnalyticsProperties.BUILD_TYPE).isEqualTo("build_type")
        assertThat(AnalyticsProperties.RUNTIME_TARGET).isEqualTo("runtime_target")
    }

    @Test
    fun `all property keys are unique`() {
        val keys = listOf(
            AnalyticsProperties.ENTRY_POINT,
            AnalyticsProperties.RESULT,
            AnalyticsProperties.SUCCESS,
            AnalyticsProperties.RESPONSE_CODE,
            AnalyticsProperties.DEBUG_MESSAGE,
            AnalyticsProperties.SOURCE,
            AnalyticsProperties.PRODUCT_ID,
            AnalyticsProperties.ENTITLEMENT_LEVEL,
            AnalyticsProperties.ENVIRONMENT,
            AnalyticsProperties.BUILD_AUDIENCE,
            AnalyticsProperties.BUILD_TYPE,
            AnalyticsProperties.RUNTIME_TARGET,
        )

        assertThat(keys.toSet()).hasSize(keys.size)
    }

    @Test
    fun `screen names are stable identifiers`() {
        assertThat(AnalyticsScreens.HOME).isEqualTo("Home")
        assertThat(AnalyticsScreens.PROTECTION).isEqualTo("Protection")
    }
}

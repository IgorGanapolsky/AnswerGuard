package com.igorganapolsky.answerguard.billing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MonetizationSourcesTest {
    @Test
    fun `paywall source constant matches expected wire value`() {
        assertThat(MonetizationSources.PAYWALL).isEqualTo("paywall")
    }

    @Test
    fun `auto restore source constant matches expected wire value`() {
        assertThat(MonetizationSources.AUTO_RESTORE).isEqualTo("auto_restore")
    }

    @Test
    fun `billing callback source constant matches expected wire value`() {
        assertThat(MonetizationSources.BILLING_CALLBACK).isEqualTo("billing_callback")
    }

    @Test
    fun `source constants are unique`() {
        val sources = setOf(
            MonetizationSources.PAYWALL,
            MonetizationSources.AUTO_RESTORE,
            MonetizationSources.BILLING_CALLBACK,
        )
        assertThat(sources).hasSize(3)
    }
}

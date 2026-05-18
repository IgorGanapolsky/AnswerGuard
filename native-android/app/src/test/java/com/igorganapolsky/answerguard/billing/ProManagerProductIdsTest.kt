package com.igorganapolsky.answerguard.billing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProManagerProductIdsTest {
    @Test
    fun `base product id matches store SKU`() {
        assertThat(ProManager.BASE_PRODUCT_ID).isEqualTo("answerguard_pro")
    }

    @Test
    fun `elite product id matches store SKU`() {
        assertThat(ProManager.ELITE_PRODUCT_ID).isEqualTo("answerguard_family")
    }

    @Test
    fun `legacy PRO_PRODUCT_ID alias resolves to elite product`() {
        assertThat(ProManager.PRO_PRODUCT_ID).isEqualTo(ProManager.ELITE_PRODUCT_ID)
    }

    @Test
    fun `base and elite product ids are distinct`() {
        assertThat(ProManager.BASE_PRODUCT_ID).isNotEqualTo(ProManager.ELITE_PRODUCT_ID)
    }
}

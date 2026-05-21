package com.igorganapolsky.answerguard.billing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EntitlementLevelTest {
    @Test
    fun `none is not pro`() {
        assertThat(EntitlementLevel.NONE.isPro).isFalse()
    }

    @Test
    fun `paid levels are pro`() {
        assertThat(EntitlementLevel.PRO.isPro).isTrue()
        assertThat(EntitlementLevel.FAMILY.isPro).isTrue()
        assertThat(EntitlementLevel.BUSINESS.isPro).isTrue()
    }
}

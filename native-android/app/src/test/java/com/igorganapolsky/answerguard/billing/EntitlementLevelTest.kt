package com.igorganapolsky.answerguard.billing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EntitlementLevelTest {
    @Test
    fun `only paid entitlement levels are pro`() {
        assertThat(EntitlementLevel.NONE.isPro).isFalse()
        assertThat(EntitlementLevel.PRO.isPro).isTrue()
        assertThat(EntitlementLevel.FAMILY.isPro).isTrue()
    }
}

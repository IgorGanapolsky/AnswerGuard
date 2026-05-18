package com.igorganapolsky.answerguard.billing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EntitlementLevelMoreTest {
    @Test
    fun `enum exposes exactly three levels`() {
        assertThat(EntitlementLevel.entries).hasSize(3)
    }

    @Test
    fun `enum entries include the expected names`() {
        assertThat(EntitlementLevel.entries.map { it.name })
            .containsExactly("NONE", "PRO", "FAMILY")
    }

    @Test
    fun `valueOf resolves canonical names`() {
        assertThat(EntitlementLevel.valueOf("NONE")).isEqualTo(EntitlementLevel.NONE)
        assertThat(EntitlementLevel.valueOf("PRO")).isEqualTo(EntitlementLevel.PRO)
        assertThat(EntitlementLevel.valueOf("FAMILY")).isEqualTo(EntitlementLevel.FAMILY)
    }

    @Test
    fun `valueOf throws for unknown level`() {
        try {
            EntitlementLevel.valueOf("UNKNOWN")
            throw AssertionError("Expected IllegalArgumentException for unknown level")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `isPro is true for every non-NONE level`() {
        val proLevels = EntitlementLevel.entries.filter { it != EntitlementLevel.NONE }

        assertThat(proLevels.all { it.isPro }).isTrue()
    }
}

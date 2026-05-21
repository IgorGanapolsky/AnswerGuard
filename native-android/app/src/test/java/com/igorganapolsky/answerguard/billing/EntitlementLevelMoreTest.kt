package com.igorganapolsky.answerguard.billing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EntitlementLevelMoreTest {
    @Test
    fun `enum exposes exactly four levels`() {
        assertThat(EntitlementLevel.entries).hasSize(4)
    }

    @Test
    fun `enum entries include the expected names`() {
        assertThat(EntitlementLevel.entries.map { it.name })
            .containsExactly("NONE", "PRO", "FAMILY", "BUSINESS")
    }

    @Test
    fun `valueOf resolves canonical names`() {
        assertThat(EntitlementLevel.valueOf("NONE")).isEqualTo(EntitlementLevel.NONE)
        assertThat(EntitlementLevel.valueOf("PRO")).isEqualTo(EntitlementLevel.PRO)
        assertThat(EntitlementLevel.valueOf("FAMILY")).isEqualTo(EntitlementLevel.FAMILY)
        assertThat(EntitlementLevel.valueOf("BUSINESS")).isEqualTo(EntitlementLevel.BUSINESS)
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

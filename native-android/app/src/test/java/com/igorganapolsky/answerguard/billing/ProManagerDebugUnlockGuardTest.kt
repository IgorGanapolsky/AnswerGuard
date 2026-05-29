package com.igorganapolsky.answerguard.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the developer-backdoor gate that unlocks paid tiers without a real
 * Play Billing purchase. **Release builds must never allow this** — letting an
 * end user reach paid features by long-pressing a button is a Play Payments
 * policy violation that would get the app banned.
 */
class ProManagerDebugUnlockGuardTest {
    @Test
    fun `canUseDebugUnlock returns true in debug builds`() {
        assertTrue(ProManager.canUseDebugUnlock(isDebugBuild = true))
    }

    @Test
    fun `canUseDebugUnlock returns false in release builds — Play Payments policy`() {
        assertFalse(
            "Pro backdoor must be disabled in release builds; otherwise Play will " +
                "reject the listing for circumventing in-app billing.",
            ProManager.canUseDebugUnlock(isDebugBuild = false),
        )
    }
}

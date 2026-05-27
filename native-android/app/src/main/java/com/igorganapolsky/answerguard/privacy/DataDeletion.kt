package com.igorganapolsky.answerguard.privacy

import android.content.Context

/**
 * Wipes every SharedPreferences file AnswerGuard has ever written, so the user
 * can fully delete their data without uninstalling the app. Backs the
 * `Settings → Delete my data` action and satisfies Play's Data Safety
 * "user-initiated data deletion" requirement.
 *
 * **Scope:** strictly on-device data. Server-side analytics (PostHog) and
 * crash reports (Firebase Crashlytics) are tied to a random per-install
 * distinct_id; rotating that ID is a follow-up for v1.2.9 — the privacy
 * policy already documents the email-based deletion path for that data in
 * the meantime.
 *
 * **Pro entitlement:** the `pro_prefs` cache is cleared too, but the user's
 * actual entitlement lives in Play Billing and will be re-resolved by
 * [com.igorganapolsky.answerguard.billing.ProManager.restorePurchases] on
 * the next launch. So clearing data won't strip a paid Pro subscription.
 */
object DataDeletion {

    /**
     * SharedPreferences file names this app has ever written. New stores
     * MUST be added here too — there's a unit test
     * (`DataDeletionTest.deletes_every_known_prefs_file`) that fails CI
     * if a new `getSharedPreferences(...)` call ships without a
     * matching entry.
     */
    internal val PREFS_FILES = listOf(
        "answerguard_screening_log", // ScreeningLog
        "answerguard_blocklist", // UserBlocklist
        "answerguard_state", // PauseState
        "answerguard_prompts", // SeenSeeAllCallsPrompt
        "answerguard_analytics", // AnalyticsService distinct_id
        "review_prefs", // StoreReviewManager
        "monetization_prefs", // ProManager HVA counts
        "pro_prefs", // ProManager entitlement cache (re-resolved from Play)
    )

    /**
     * Clears every known SharedPreferences file. Returns the number of
     * stores wiped (useful for telemetry and unit tests).
     */
    fun deleteAllUserData(context: Context): Int {
        var cleared = 0
        for (name in PREFS_FILES) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
            cleared += 1
        }
        return cleared
    }
}

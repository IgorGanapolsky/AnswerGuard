package com.igorganapolsky.answerguard.screening

import android.content.Context
import androidx.core.content.edit

/**
 * One-shot flag: have we already prompted the user for CALL_LOG + VOICEMAIL?
 *
 * We only ask once per install. If the user denies, they can re-grant from
 * Settings; nagging on every launch is worse than missing a few weeks of
 * back-filled calls.
 */
object SeenSeeAllCallsPrompt {
    private const val PREFS = "answerguard_prompts"
    private const val KEY = "see_all_calls_prompt_shown"

    fun wasShown(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun markShown(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit(commit = true) { putBoolean(KEY, true) }
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit(commit = true) { remove(KEY) }
    }
}

package com.igorganapolsky.answerguard.screening

import android.content.Context
import androidx.core.content.edit

/**
 * In-app pause flag for call screening.
 *
 * Android does not let an app revoke its own RoleManager role, so "off" is
 * modelled as a boolean here: when paused, AnswerGuardScreeningService still
 * binds for each incoming call but short-circuits to allow the call through
 * unmodified. This is the same pattern RoboKiller / Hiya / Truecaller use.
 */
object PauseState {

    private const val PREFS_NAME = "answerguard_state"
    private const val KEY_PAUSED = "screening_paused"
    private const val KEY_CONTACTS_ENABLED = "contact_identification_enabled"

    fun isPaused(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PAUSED, false)

    fun setPaused(context: Context, paused: Boolean) {
        prefs(context).edit { putBoolean(KEY_PAUSED, paused) }
    }

    fun isContactIdentificationEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CONTACTS_ENABLED, true)

    fun setContactIdentificationEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_CONTACTS_ENABLED, enabled) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

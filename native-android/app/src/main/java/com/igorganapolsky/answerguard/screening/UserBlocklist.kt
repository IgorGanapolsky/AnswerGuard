package com.igorganapolsky.answerguard.screening

import android.content.Context
import androidx.core.content.edit

/**
 * Simple SharedPreferences-backed user blocklist.
 * Must be initialized via [UserBlocklist.init] before first use (e.g. in Application.onCreate).
 */
object UserBlocklist {

    private const val PREFS_NAME = "answerguard_blocklist"
    private const val KEY_NUMBERS = "blocked_numbers"

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun contains(digits: String): Boolean {
        return getAll().contains(digits)
    }

    fun add(digits: String) {
        val updated = getAll().toMutableSet().also { it.add(digits) }
        save(updated)
    }

    fun remove(digits: String) {
        val updated = getAll().toMutableSet().also { it.remove(digits) }
        save(updated)
    }

    fun getAll(): Set<String> {
        return prefs?.getStringSet(KEY_NUMBERS, emptySet()) ?: emptySet()
    }

    private fun save(numbers: Set<String>) {
        prefs?.edit { putStringSet(KEY_NUMBERS, numbers) }
    }
}

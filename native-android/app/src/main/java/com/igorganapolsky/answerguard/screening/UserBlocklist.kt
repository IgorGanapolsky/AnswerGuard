package com.igorganapolsky.answerguard.screening

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple SharedPreferences-backed user blocklist.
 * Must be initialized via [UserBlocklist.init] before first use (e.g. in Application.onCreate).
 */
object UserBlocklist {

    private const val PREFS_NAME = "answerguard_blocklist"
    private const val KEY_NUMBERS = "blocked_numbers"

    private var prefs: android.content.SharedPreferences? = null
    
    private val _blockedNumbers = MutableStateFlow<Set<String>>(emptySet())
    val blockedNumbers: StateFlow<Set<String>> = _blockedNumbers.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _blockedNumbers.value = getAll()
    }

    fun contains(digits: String): Boolean {
        return _blockedNumbers.value.contains(digits)
    }

    fun add(digits: String) {
        val updated = getAll().toMutableSet().also { it.add(digits) }
        save(updated)
        _blockedNumbers.value = updated
    }

    fun remove(digits: String) {
        val updated = getAll().toMutableSet().also { it.remove(digits) }
        save(updated)
        _blockedNumbers.value = updated
    }

    fun getAll(): Set<String> {
        return prefs?.getStringSet(KEY_NUMBERS, emptySet()) ?: emptySet()
    }

    private fun save(numbers: Set<String>) {
        prefs?.edit { putStringSet(KEY_NUMBERS, numbers) }
    }
}

package com.igorganapolsky.answerguard.screening

import android.util.Log

/**
 * Local-first, zero-knowledge offline Caller ID database.
 * Maps known phone numbers (digits-only) to identified business/entity names.
 */
object CallerIdDatabase {

    private val tag = "CallerIdDatabase"

    // Locally resolved offline business/entity directory
    private val directory = mapOf(
        "18559975360" to "Google Fi Voicemail",
        "18056377243" to "T-Mobile Voicemail",
        "18668223348" to "Verizon Voicemail Retrieval",
        "18882446245" to "AT&T Voicemail Retrieval",
        "19544483475" to "ABC Puerto Rico",
        "18706888127" to "Unsubscribe Campaign",
        "14082560351" to "Spam Telemarketer",
        "18884021096" to "Scam Likely",
        "9544940469" to "Potential Spam",
        "19544940469" to "Potential Spam"
    )

    fun identify(number: String): String? {
        val digits = number.filter { it.isDigit() }
        if (digits.isBlank()) return null
        
        // Match exact, with leading 1, or without leading 1
        val name = directory[digits]
            ?: directory["1$digits"]
            ?: directory[digits.removePrefix("1")]
            
        if (name != null) {
            Log.d(tag, "Number $digits identified as: $name")
        }
        return name
    }
}

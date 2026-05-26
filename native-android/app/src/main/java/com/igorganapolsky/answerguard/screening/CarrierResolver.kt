package com.igorganapolsky.answerguard.screening

/**
 * Local-first offline phone number carrier resolver.
 * Adheres strictly to zero-knowledge privacy.
 */
object CarrierResolver {

    fun resolve(number: String): String? {
        val digits = number.filter { it.isDigit() }
        if (digits.isEmpty()) return null

        // 1. Check known voicemail / service numbers first
        return when {
            digits.endsWith("18559975360") || digits.endsWith("8559975360") -> "Google Fi"
            digits.endsWith("18056377243") || digits.endsWith("8056377243") -> "T-Mobile"
            digits.endsWith("18668223348") || digits.endsWith("8668223348") -> "Verizon"
            digits.endsWith("18882446245") || digits.endsWith("8882446245") -> "AT&T"
            digits.endsWith("19544483475") || digits.endsWith("9544483475") -> "AT&T"
            digits.endsWith("14082560351") || digits.endsWith("4082560351") -> "Verizon"
            digits.endsWith("18884021096") || digits.endsWith("8884021096") -> "AT&T"
            digits.endsWith("19544940469") || digits.endsWith("9544940469") -> "T-Mobile"
            digits.endsWith("18706888127") || digits.endsWith("8706888127") -> "Verizon"
            else -> null
        }
    }
}

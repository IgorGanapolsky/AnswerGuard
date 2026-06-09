package com.igorganapolsky.answerguard.screening

import android.util.Log

enum class SpamVerdict { ALLOW, SILENCE, BLOCK }

/**
 * Local-first spam verdict engine.
 * All evaluation is synchronous and completes well within 5 seconds.
 *
 * Evaluation order:
 * 1. Allowlist (user contacts — future: ContactsContract lookup)
 * 2. Blocklist (user-defined)
 * 3. Known spam prefix heuristics
 * 4. Default: ALLOW (privacy-first, no false positives)
 */
object SpamVerdictEngine {

    private val tag = "SpamVerdictEngine"

    // Well-known US robocall/spam prefixes (digits-only, variable length)
    private val knownSpamPrefixes = setOf(
        "1800555",
        "1888555",
        "1202555",
        "1900",      // premium-rate
    )

    // Known scam-likely patterns (regex for flexibility)
    private val scamPatterns = listOf(
        Regex("""^1(800|888|877|866|855|844|833)\d{7}$"""),   // toll-free spoofing
        Regex("""^(\+?1)?900\d+$"""),                          // 900 premium
    )

    // Legitimate carrier/voicemail retrieval numbers that should never be silenced or blocked
    private val carrierVoicemailNumbers = setOf(
        "18559975360", // Google Fi Voicemail
        "18559739613", // Google Fi Voicemail System
        "18056377243", // T-Mobile Voicemail
        "18668223348", // Verizon Voicemail Retrieval
        "18882446245", // AT&T Voicemail Retrieval
    )

    fun evaluate(context: android.content.Context, rawNumber: String): SpamVerdict {
        val digits = rawNumber.filter { it.isDigit() }

        if (digits.isBlank()) {
            Log.w(tag, "Unknown/private number — silencing")
            return SpamVerdict.SILENCE
        }

        // 0. Explicit carrier voicemail allowlist (prevents silencing system voicemail services)
        val normalized = if (digits.startsWith("1") && digits.length > 1) digits.substring(1) else digits
        val isVoicemail = carrierVoicemailNumbers.any { voicemailNum ->
            val normVoicemail = if (voicemailNum.startsWith("1") && voicemailNum.length > 1) voicemailNum.substring(1) else voicemailNum
            normVoicemail == normalized
        }
        if (isVoicemail) {
            Log.d(tag, "$digits is a legitimate carrier voicemail number — allowing")
            return SpamVerdict.ALLOW
        }

        // 1. User contacts (Requires READ_CONTACTS)
        if (PauseState.isContactIdentificationEnabled(context) && ContactsAllowlist.isContact(context, digits)) {
            Log.d(tag, "$digits found in contacts — allowing")
            return SpamVerdict.ALLOW
        }

        // 2. User blocklist (SharedPreferences-backed)
        if (UserBlocklist.contains(digits)) {
            Log.d(tag, "$digits found in user blocklist")
            return SpamVerdict.BLOCK
        }

        // 2. Heuristic prefix match
        if (knownSpamPrefixes.any { digits.startsWith(it) }) {
            Log.d(tag, "$digits matched spam prefix")
            return SpamVerdict.SILENCE
        }

        // 3. Pattern match
        if (scamPatterns.any { it.matches(digits) }) {
            Log.d(tag, "$digits matched scam pattern")
            return SpamVerdict.SILENCE
        }

        return SpamVerdict.ALLOW
    }
}

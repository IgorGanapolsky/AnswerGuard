package com.igorganapolsky.answerguard.screening

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.VoicemailContract
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Bridges AG's local [ScreeningLog] with the system-wide records of calls that
 * AG's [AnswerGuardScreeningService] never saw:
 *
 * - **CallLog backfill**: Android still logs DND-silenced calls and carrier-
 *   side OS-blocked calls; these never invoke our CallScreeningService but
 *   they live in [CallLog.Calls].
 *
 * - **Voicemail backfill**: when a carrier (e.g. Google Fi) routes a flagged
 *   call direct-to-voicemail at the network level, zero telephony events reach
 *   the device — the only proof the call existed is a row dropped into
 *   [VoicemailContract.Voicemails].
 *
 * Backfill is foreground-only: invoked from the home screen refresh so we
 * don't add background polling. Entries are merged into [ScreeningLog]
 * de-duplicated by (digits-only number, timestamp ± dedupe window).
 */
object CallEventBackfill {

    private const val TAG = "CallEventBackfill"

    /** Look-back window for backfill. 30 days lines up with typical call-log
     *  retention on most OEMs and keeps the SharedPreferences log bounded. */
    private const val LOOKBACK_MS = 30L * 24 * 60 * 60 * 1000

    /** Two events on the same number within this window are considered the same
     *  underlying call (e.g. CallScreeningService + CallLog row + voicemail). */
    private const val DEDUPE_WINDOW_MS = 60L * 1000

    fun hasCallLogPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    fun hasVoicemailPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            "com.android.voicemail.permission.READ_VOICEMAIL",
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Reads recent CallLog + Voicemail entries and inserts any that aren't
     * already in [ScreeningLog]. Returns the number of new rows inserted.
     */
    fun merge(context: Context): Int {
        val existing = ScreeningLog.getRecent()
        val cutoff = System.currentTimeMillis() - LOOKBACK_MS

        val newEntries = mutableListOf<ScreenedCall>()
        if (hasCallLogPermission(context)) {
            newEntries += readCallLog(context, cutoff)
        }
        if (hasVoicemailPermission(context)) {
            newEntries += readVoicemails(context, cutoff)
        }

        // Dedupe against existing log and within the batch itself.
        val accepted = mutableListOf<ScreenedCall>()
        val seen = existing.toMutableList()
        for (entry in newEntries.sortedByDescending { it.timestamp }) {
            if (!isDuplicate(entry, seen)) {
                accepted += entry
                seen += entry
            }
        }

        // Insert oldest-first so the most recent ends up at index 0.
        accepted.sortedBy { it.timestamp }.forEach { ScreeningLog.record(it) }
        return accepted.size
    }

    private fun isDuplicate(candidate: ScreenedCall, existing: List<ScreenedCall>): Boolean {
        val candidateDigits = candidate.number.digitsOnly()
        if (candidateDigits.isEmpty()) return true
        return existing.any { other ->
            other.number.digitsOnly() == candidateDigits &&
                kotlin.math.abs(other.timestamp - candidate.timestamp) <= DEDUPE_WINDOW_MS
        }
    }

    @SuppressLint("MissingPermission") // gated by hasCallLogPermission
    private fun readCallLog(context: Context, cutoff: Long): List<ScreenedCall> {
        val out = mutableListOf<ScreenedCall>()
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.TYPE,
            CallLog.Calls.CACHED_NAME,
        )
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                "${CallLog.Calls.DATE} >= ?",
                arrayOf(cutoff.toString()),
                "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                while (cursor.moveToNext()) {
                    val number = cursor.getString(numberIdx).orEmpty()
                    if (number.digitsOnly().isEmpty()) continue
                    val date = cursor.getLong(dateIdx)
                    val type = cursor.getInt(typeIdx)
                    val cachedName = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    val verdict = callTypeToVerdict(type) ?: continue
                    out += ScreenedCall(
                        number = number,
                        verdict = verdict,
                        timestamp = date,
                        callType = "CALL",
                        senderName = cachedName?.takeIf { it.isNotBlank() },
                        source = CallSource.SYSTEM_CALL_LOG,
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "CallLog read failed: ${t.message}")
        }
        return out
    }

    @SuppressLint("MissingPermission") // gated by hasVoicemailPermission
    private fun readVoicemails(context: Context, cutoff: Long): List<ScreenedCall> {
        val out = mutableListOf<ScreenedCall>()
        val projection = arrayOf(
            VoicemailContract.Voicemails.NUMBER,
            VoicemailContract.Voicemails.DATE,
        )
        try {
            context.contentResolver.query(
                VoicemailContract.Voicemails.CONTENT_URI,
                projection,
                "${VoicemailContract.Voicemails.DATE} >= ?",
                arrayOf(cutoff.toString()),
                "${VoicemailContract.Voicemails.DATE} DESC",
            )?.use { cursor ->
                val numberIdx = cursor.getColumnIndex(VoicemailContract.Voicemails.NUMBER)
                val dateIdx = cursor.getColumnIndex(VoicemailContract.Voicemails.DATE)
                while (cursor.moveToNext()) {
                    val number = if (numberIdx >= 0) cursor.getString(numberIdx).orEmpty() else ""
                    if (number.digitsOnly().isEmpty()) continue
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else continue
                    // Carrier-filtered voicemails are presumed unwanted; the user
                    // can override per-number with the Block/Unblock controls.
                    out += ScreenedCall(
                        number = number,
                        verdict = SpamVerdict.SILENCE,
                        timestamp = date,
                        callType = "CALL",
                        senderName = null,
                        source = CallSource.VOICEMAIL,
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Voicemail read failed: ${t.message}")
        }
        return out
    }

    private fun callTypeToVerdict(type: Int): SpamVerdict? = when (type) {
        CallLog.Calls.MISSED_TYPE -> SpamVerdict.SILENCE
        CallLog.Calls.REJECTED_TYPE -> SpamVerdict.BLOCK
        CallLog.Calls.BLOCKED_TYPE -> SpamVerdict.BLOCK
        CallLog.Calls.INCOMING_TYPE -> SpamVerdict.ALLOW
        CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> SpamVerdict.ALLOW
        // Outgoing and voicemail entries are not surfaced as screening events
        // (voicemail rows are handled separately above with VOICEMAIL source).
        else -> null
    }

    private fun String.digitsOnly(): String = filter { it.isDigit() }
}

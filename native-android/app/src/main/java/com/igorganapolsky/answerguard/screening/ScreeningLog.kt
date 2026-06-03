package com.igorganapolsky.answerguard.screening

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Source of a [ScreenedCall] record.
 *
 * AG's CallScreeningService only fires for calls the OS actually rings. DND-
 * silenced and carrier-spam-filtered calls are surfaced post-hoc by reading
 * the system CallLog and Voicemail content providers — see [CallEventBackfill].
 */
enum class CallSource {
    /** Logged by AG's own CallScreeningService at ring time. */
    SCREENING,

    /** Backfilled from android.provider.CallLog — call rang the device but
     *  AG didn't process it (e.g. DND silenced, AG not yet the default app). */
    SYSTEM_CALL_LOG,

    /** Backfilled from android.provider.VoicemailContract — carrier routed the
     *  call direct-to-voicemail and the device never received it as a call. */
    VOICEMAIL,
}

data class ScreenedCall(
    val number: String,
    val verdict: SpamVerdict,
    val timestamp: Long = System.currentTimeMillis(),
    val callType: String = "CALL", // "CALL" or "SMS"
    val senderName: String? = null, // Identified caller/sender name
    val source: CallSource = CallSource.SCREENING,
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("number", number)
            put("verdict", verdict.name)
            put("timestamp", timestamp)
            put("callType", callType)
            put("senderName", senderName ?: "")
            put("source", source.name)
        }.toString()
    }

    companion object {
        fun fromJson(json: String): ScreenedCall {
            val obj = JSONObject(json)
            val sourceName = obj.optString("source", CallSource.SCREENING.name)
            val source = runCatching { CallSource.valueOf(sourceName) }
                .getOrDefault(CallSource.SCREENING)
            return ScreenedCall(
                number = obj.getString("number"),
                verdict = SpamVerdict.valueOf(obj.getString("verdict")),
                timestamp = obj.getLong("timestamp"),
                callType = obj.optString("callType", "CALL"),
                senderName = obj.optString("senderName", "").takeIf { it.isNotEmpty() },
                source = source,
            )
        }
    }
}

object ScreeningLog {
    private const val PREFS_NAME = "answerguard_screening_log"
    private const val KEY_LOG = "screened_calls"
    private const val MAX_LOG_SIZE = 50

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun record(call: ScreenedCall) {
        val current = getRecent().toMutableList()
        current.add(0, call)
        val limited = current.take(MAX_LOG_SIZE)
        save(limited)
    }

    fun getRecent(): List<ScreenedCall> {
        val json = prefs?.getString(KEY_LOG, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i -> ScreenedCall.fromJson(arr.getString(i)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clear() {
        save(emptyList())
    }

    private fun save(calls: List<ScreenedCall>) {
        val arr = JSONArray()
        calls.forEach { arr.put(it.toJson()) }
        prefs?.edit(commit = true) { putString(KEY_LOG, arr.toString()) }
    }
}


package com.igorganapolsky.answerguard.screening

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

enum class ScreeningVerdict { ALLOWED, SILENCED, BLOCKED }

data class ScreeningEvent(
    val id: String,
    val phoneNumber: String,
    val verdict: ScreeningVerdict,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Privacy-preserving ephemeral screening log.
 * Stores last 50 events locally using org.json.
 */
object ScreeningLog {
    private const val PREFS_NAME = "answerguard_log"
    private const val KEY_EVENTS = "recent_events"
    private const val MAX_LOG_SIZE = 50

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun log(event: ScreeningEvent) {
        val current = getEvents().toMutableList()
        current.add(0, event)
        val trimmed = current.take(MAX_LOG_SIZE)
        save(trimmed)
    }

    fun getEvents(): List<ScreeningEvent> {
        val raw = prefs?.getString(KEY_EVENTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<ScreeningEvent>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(ScreeningEvent(
                    id = obj.getString("id"),
                    phoneNumber = obj.getString("phoneNumber"),
                    verdict = ScreeningVerdict.valueOf(obj.getString("verdict")),
                    reason = obj.getString("reason"),
                    timestamp = obj.getLong("timestamp")
                ))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(events: List<ScreeningEvent>) {
        val array = JSONArray()
        events.forEach { event ->
            val obj = JSONObject().apply {
                put("id", event.id)
                put("phoneNumber", event.phoneNumber)
                put("verdict", event.verdict.name)
                put("reason", event.reason)
                put("timestamp", event.timestamp)
            }
            array.put(obj)
        }
        prefs?.edit { putString(KEY_EVENTS, array.toString()) }
    }
}

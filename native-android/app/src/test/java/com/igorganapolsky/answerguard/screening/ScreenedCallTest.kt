package com.igorganapolsky.answerguard.screening

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test

class ScreenedCallTest {

    @Test
    fun `toJson includes all fields`() {
        val call = ScreenedCall(
            number = "16175550100",
            verdict = SpamVerdict.BLOCK,
            timestamp = 1700000000000L,
            callType = "CALL",
            senderName = "Spammer"
        )

        val obj = JSONObject(call.toJson())

        assertThat(obj.getString("number")).isEqualTo("16175550100")
        assertThat(obj.getString("verdict")).isEqualTo("BLOCK")
        assertThat(obj.getLong("timestamp")).isEqualTo(1700000000000L)
        assertThat(obj.getString("callType")).isEqualTo("CALL")
        assertThat(obj.getString("senderName")).isEqualTo("Spammer")
    }

    @Test
    fun `toJson encodes null senderName as empty string`() {
        val call = ScreenedCall(
            number = "16175550100",
            verdict = SpamVerdict.ALLOW,
            timestamp = 1L,
            senderName = null
        )

        val obj = JSONObject(call.toJson())
        assertThat(obj.getString("senderName")).isEmpty()
    }

    @Test
    fun `fromJson restores all fields`() {
        val json = JSONObject().apply {
            put("number", "16175550100")
            put("verdict", "SILENCE")
            put("timestamp", 1700000000000L)
            put("callType", "SMS")
            put("senderName", "Friend")
        }.toString()

        val call = ScreenedCall.fromJson(json)

        assertThat(call.number).isEqualTo("16175550100")
        assertThat(call.verdict).isEqualTo(SpamVerdict.SILENCE)
        assertThat(call.timestamp).isEqualTo(1700000000000L)
        assertThat(call.callType).isEqualTo("SMS")
        assertThat(call.senderName).isEqualTo("Friend")
    }

    @Test
    fun `fromJson treats empty senderName as null`() {
        val json = JSONObject().apply {
            put("number", "16175550100")
            put("verdict", "ALLOW")
            put("timestamp", 1L)
            put("callType", "CALL")
            put("senderName", "")
        }.toString()

        val call = ScreenedCall.fromJson(json)

        assertThat(call.senderName).isNull()
    }

    @Test
    fun `fromJson defaults callType to CALL when missing`() {
        val json = JSONObject().apply {
            put("number", "16175550100")
            put("verdict", "ALLOW")
            put("timestamp", 1L)
        }.toString()

        val call = ScreenedCall.fromJson(json)

        assertThat(call.callType).isEqualTo("CALL")
    }

    @Test
    fun `fromJson defaults senderName to null when missing`() {
        val json = JSONObject().apply {
            put("number", "16175550100")
            put("verdict", "ALLOW")
            put("timestamp", 1L)
        }.toString()

        val call = ScreenedCall.fromJson(json)

        assertThat(call.senderName).isNull()
    }

    @Test
    fun `toJson and fromJson roundtrip preserves all values`() {
        val original = ScreenedCall(
            number = "18005551234",
            verdict = SpamVerdict.SILENCE,
            timestamp = 9999L,
            callType = "SMS",
            senderName = "Toll-Free Spam"
        )

        val restored = ScreenedCall.fromJson(original.toJson())

        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun `toJson and fromJson roundtrip preserves null senderName`() {
        val original = ScreenedCall(
            number = "18005551234",
            verdict = SpamVerdict.ALLOW,
            timestamp = 1L,
            callType = "CALL",
            senderName = null
        )

        val restored = ScreenedCall.fromJson(original.toJson())

        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun `default timestamp uses current time`() {
        val before = System.currentTimeMillis()
        val call = ScreenedCall(number = "1", verdict = SpamVerdict.ALLOW)
        val after = System.currentTimeMillis()

        assertThat(call.timestamp).isAtLeast(before)
        assertThat(call.timestamp).isAtMost(after)
    }

    @Test
    fun `default callType is CALL`() {
        val call = ScreenedCall(number = "1", verdict = SpamVerdict.ALLOW, timestamp = 1L)
        assertThat(call.callType).isEqualTo("CALL")
    }

    @Test
    fun `default senderName is null`() {
        val call = ScreenedCall(number = "1", verdict = SpamVerdict.ALLOW, timestamp = 1L)
        assertThat(call.senderName).isNull()
    }
}

package com.igorganapolsky.answerguard.screening

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

class ScreeningLogTest {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private var storedJson: String = "[]"

    @Before
    fun setUp() {
        context = mockk()
        prefs = mockk()
        editor = mockk()
        storedJson = "[]"

        every {
            context.getSharedPreferences("answerguard_screening_log", Context.MODE_PRIVATE)
        } returns prefs
        every { prefs.getString("screened_calls", "[]") } answers { storedJson }
        every { prefs.edit() } returns editor
        every { editor.putString("screened_calls", any()) } answers {
            storedJson = secondArg()
            editor
        }
        every { editor.commit() } returns true

        ScreeningLog.init(context)
        // Reset between tests via clear()
        ScreeningLog.clear()
    }

    @Test
    fun `getRecent is empty before any calls are recorded`() {
        assertThat(ScreeningLog.getRecent()).isEmpty()
    }

    @Test
    fun `record stores the call and getRecent returns it`() {
        val call = ScreenedCall(
            number = "16175550100",
            verdict = SpamVerdict.ALLOW,
            timestamp = 1700000000000L,
            callType = "CALL",
            senderName = "Test Caller"
        )

        ScreeningLog.record(call)

        val recent = ScreeningLog.getRecent()
        assertThat(recent).hasSize(1)
        assertThat(recent[0].number).isEqualTo("16175550100")
        assertThat(recent[0].verdict).isEqualTo(SpamVerdict.ALLOW)
        assertThat(recent[0].timestamp).isEqualTo(1700000000000L)
        assertThat(recent[0].callType).isEqualTo("CALL")
        assertThat(recent[0].senderName).isEqualTo("Test Caller")
    }

    @Test
    fun `record prepends so newest is first`() {
        ScreeningLog.record(ScreenedCall("111", SpamVerdict.ALLOW, 1L))
        ScreeningLog.record(ScreenedCall("222", SpamVerdict.BLOCK, 2L))
        ScreeningLog.record(ScreenedCall("333", SpamVerdict.SILENCE, 3L))

        val recent = ScreeningLog.getRecent()
        assertThat(recent.map { it.number }).containsExactly("333", "222", "111").inOrder()
    }

    @Test
    fun `clear empties the log`() {
        ScreeningLog.record(ScreenedCall("111", SpamVerdict.ALLOW, 1L))

        ScreeningLog.clear()

        assertThat(ScreeningLog.getRecent()).isEmpty()
    }

    @Test
    fun `record caps log at MAX_LOG_SIZE entries`() {
        for (i in 1..55) {
            ScreeningLog.record(
                ScreenedCall(
                    number = i.toString(),
                    verdict = SpamVerdict.ALLOW,
                    timestamp = i.toLong()
                )
            )
        }

        val recent = ScreeningLog.getRecent()
        assertThat(recent).hasSize(50)
        // newest first
        assertThat(recent.first().number).isEqualTo("55")
        // entries older than the 50th most recent are dropped
        assertThat(recent.map { it.number }).doesNotContain("5")
    }

    @Test
    fun `getRecent returns empty list when stored JSON is malformed`() {
        storedJson = "not-valid-json"

        assertThat(ScreeningLog.getRecent()).isEmpty()
    }

    @Test
    fun `record persists call type and senderName for SMS`() {
        ScreeningLog.record(
            ScreenedCall(
                number = "16175550100",
                verdict = SpamVerdict.SILENCE,
                timestamp = 42L,
                callType = "SMS",
                senderName = "Spammer"
            )
        )

        val recent = ScreeningLog.getRecent()
        assertThat(recent).hasSize(1)
        assertThat(recent[0].callType).isEqualTo("SMS")
        assertThat(recent[0].senderName).isEqualTo("Spammer")
    }

    @Test
    fun `record persists null senderName as null on read`() {
        ScreeningLog.record(
            ScreenedCall(
                number = "16175550100",
                verdict = SpamVerdict.ALLOW,
                timestamp = 42L,
                senderName = null
            )
        )

        val recent = ScreeningLog.getRecent()
        assertThat(recent).hasSize(1)
        assertThat(recent[0].senderName).isNull()
    }
}

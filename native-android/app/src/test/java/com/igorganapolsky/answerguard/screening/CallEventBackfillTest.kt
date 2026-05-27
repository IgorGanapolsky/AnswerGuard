package com.igorganapolsky.answerguard.screening

import android.app.Application
import android.provider.CallLog
import android.provider.VoicemailContract
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboCursor

/**
 * Verifies [CallEventBackfill] surfaces CallLog and Voicemail rows AnswerGuard's
 * CallScreeningService never saw, without double-inserting entries that already
 * live in [ScreeningLog].
 *
 * Robolectric's stock CallLog/Voicemail provider stubs don't store inserted
 * rows, so we install pre-built [MatrixCursor]s on the shadow ContentResolver
 * for the URIs the production code queries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CallEventBackfillTest {

    private lateinit var context: Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ScreeningLog.init(context)
        ScreeningLog.clear()
    }

    @After
    fun tearDown() {
        ScreeningLog.clear()
        shadowOf(context.contentResolver).setCursor(CallLog.Calls.CONTENT_URI, null)
        shadowOf(context.contentResolver).setCursor(VoicemailContract.Voicemails.CONTENT_URI, null)
    }

    private fun grantCallLog() {
        shadowOf(context).grantPermissions(android.Manifest.permission.READ_CALL_LOG)
    }

    private fun grantVoicemail() {
        shadowOf(context).grantPermissions("com.android.voicemail.permission.READ_VOICEMAIL")
    }

    private fun installCallLogRows(rows: List<CallLogRow>) {
        val cursor = RoboCursor().apply {
            setColumnNames(
                listOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DATE,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.CACHED_NAME,
                )
            )
            setResults(
                rows.map { row ->
                    arrayOf<Any?>(row.number, row.date, row.type, row.cachedName)
                }.toTypedArray()
            )
        }
        shadowOf(context.contentResolver).setCursor(CallLog.Calls.CONTENT_URI, cursor)
    }

    private fun installVoicemailRows(rows: List<VoicemailRow>) {
        val cursor = RoboCursor().apply {
            setColumnNames(
                listOf(
                    VoicemailContract.Voicemails.NUMBER,
                    VoicemailContract.Voicemails.DATE,
                )
            )
            setResults(
                rows.map { row -> arrayOf<Any?>(row.number, row.date) }.toTypedArray()
            )
        }
        shadowOf(context.contentResolver).setCursor(VoicemailContract.Voicemails.CONTENT_URI, cursor)
    }

    private data class CallLogRow(
        val number: String,
        val date: Long,
        val type: Int,
        val cachedName: String? = null,
    )

    private data class VoicemailRow(val number: String, val date: Long)

    @Test
    fun `merge inserts nothing when no permissions are granted`() {
        val now = System.currentTimeMillis()
        installCallLogRows(listOf(CallLogRow("+17865550100", now, CallLog.Calls.MISSED_TYPE)))
        installVoicemailRows(listOf(VoicemailRow("+17865550101", now)))

        val inserted = CallEventBackfill.merge(context)

        assertThat(inserted).isEqualTo(0)
        assertThat(ScreeningLog.getRecent()).isEmpty()
    }

    @Test
    fun `merge surfaces a DND-silenced CallLog entry`() {
        grantCallLog()
        val now = System.currentTimeMillis()
        installCallLogRows(listOf(CallLogRow("+18445550100", now, CallLog.Calls.MISSED_TYPE)))

        val inserted = CallEventBackfill.merge(context)

        assertThat(inserted).isEqualTo(1)
        val recent = ScreeningLog.getRecent()
        assertThat(recent).hasSize(1)
        assertThat(recent[0].number).isEqualTo("+18445550100")
        assertThat(recent[0].verdict).isEqualTo(SpamVerdict.SILENCE)
        assertThat(recent[0].source).isEqualTo(CallSource.SYSTEM_CALL_LOG)
    }

    @Test
    fun `merge surfaces a carrier-filtered voicemail as VOICEMAIL source`() {
        grantVoicemail()
        val now = System.currentTimeMillis()
        installVoicemailRows(listOf(VoicemailRow("+17868013030", now)))

        val inserted = CallEventBackfill.merge(context)

        assertThat(inserted).isEqualTo(1)
        val recent = ScreeningLog.getRecent()
        assertThat(recent).hasSize(1)
        assertThat(recent[0].number).isEqualTo("+17868013030")
        assertThat(recent[0].verdict).isEqualTo(SpamVerdict.SILENCE)
        assertThat(recent[0].source).isEqualTo(CallSource.VOICEMAIL)
    }

    @Test
    fun `merge skips entries already present in ScreeningLog`() {
        grantCallLog()
        val now = System.currentTimeMillis()
        ScreeningLog.record(
            ScreenedCall(
                number = "+18445550100",
                verdict = SpamVerdict.BLOCK,
                timestamp = now,
                source = CallSource.SCREENING,
            )
        )
        installCallLogRows(
            listOf(CallLogRow("+18445550100", now + 5_000, CallLog.Calls.MISSED_TYPE))
        )

        val inserted = CallEventBackfill.merge(context)

        assertThat(inserted).isEqualTo(0)
        assertThat(ScreeningLog.getRecent()).hasSize(1)
    }

    @Test
    fun `merge keeps entries outside the dedupe window`() {
        grantCallLog()
        val now = System.currentTimeMillis()
        ScreeningLog.record(
            ScreenedCall(
                number = "+18445550100",
                verdict = SpamVerdict.BLOCK,
                timestamp = now,
                source = CallSource.SCREENING,
            )
        )
        // Far enough apart that they are independent calls (5 min > 60s window).
        installCallLogRows(
            listOf(CallLogRow("+18445550100", now + 5L * 60 * 1000, CallLog.Calls.MISSED_TYPE))
        )

        val inserted = CallEventBackfill.merge(context)

        assertThat(inserted).isEqualTo(1)
        assertThat(ScreeningLog.getRecent()).hasSize(2)
    }

    @Test
    fun `merge maps CallLog REJECTED and BLOCKED types to BLOCK verdict`() {
        grantCallLog()
        val now = System.currentTimeMillis()
        installCallLogRows(
            listOf(
                CallLogRow("+18445551111", now, CallLog.Calls.REJECTED_TYPE),
                CallLogRow("+18445552222", now + 1_000, CallLog.Calls.BLOCKED_TYPE),
            )
        )

        CallEventBackfill.merge(context)

        val recent = ScreeningLog.getRecent().sortedBy { it.number }
        assertThat(recent).hasSize(2)
        assertThat(recent[0].verdict).isEqualTo(SpamVerdict.BLOCK)
        assertThat(recent[1].verdict).isEqualTo(SpamVerdict.BLOCK)
    }

    @Test
    fun `merge ignores outgoing calls`() {
        grantCallLog()
        val now = System.currentTimeMillis()
        installCallLogRows(
            listOf(CallLogRow("+18445550100", now, CallLog.Calls.OUTGOING_TYPE))
        )

        val inserted = CallEventBackfill.merge(context)

        assertThat(inserted).isEqualTo(0)
        assertThat(ScreeningLog.getRecent()).isEmpty()
    }

    @Test
    fun `hasCallLogPermission reflects grant state`() {
        assertThat(CallEventBackfill.hasCallLogPermission(context)).isFalse()
        grantCallLog()
        assertThat(CallEventBackfill.hasCallLogPermission(context)).isTrue()
    }

    @Test
    fun `hasVoicemailPermission reflects grant state`() {
        assertThat(CallEventBackfill.hasVoicemailPermission(context)).isFalse()
        grantVoicemail()
        assertThat(CallEventBackfill.hasVoicemailPermission(context)).isTrue()
    }
}

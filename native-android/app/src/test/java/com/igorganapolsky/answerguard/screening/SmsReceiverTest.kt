package com.igorganapolsky.answerguard.screening

import android.app.Application
import android.content.Intent
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.igorganapolsky.answerguard.billing.ProManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric coverage for [SmsReceiver].
 *
 * Hilt is bypassed by:
 *  - Setting `application = Application::class` in @Config (avoids HiltAndroidApp init)
 *  - Pre-setting Hilt_SmsReceiver.injected via reflection so inject() is a no-op
 *  - Manually assigning the proManager lateinit field
 *
 * The message-processing branch uses real 3GPP PDU bytes in the intent because mockk's
 * static mocking cannot retransform Telephony.Sms.Intents under JDK 17 (final class
 * modifier redefinition is rejected by Instrumentation.retransformClasses0). Robolectric's
 * SmsMessage.createFromPdu parses these PDUs and returns real SmsMessage objects.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SmsReceiverTest {

    private lateinit var context: Application
    private lateinit var proManager: ProManager
    private lateinit var isProFlow: MutableStateFlow<Boolean>
    private lateinit var receiver: SmsReceiver

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ScreeningLog.init(context)
        ScreeningLog.clear()

        proManager = mockk(relaxed = true)
        isProFlow = MutableStateFlow(true)
        every { proManager.isPro } returns isProFlow

        receiver = SmsReceiver()
        receiver.proManager = proManager
        markHiltInjected(receiver)
    }

    /** Set the private `injected` boolean on Hilt_SmsReceiver to true so inject() is a no-op. */
    private fun markHiltInjected(receiver: SmsReceiver) {
        val hiltClass = receiver.javaClass.superclass // Hilt_SmsReceiver
        val field = hiltClass.getDeclaredField("injected")
        field.isAccessible = true
        field.setBoolean(receiver, true)
    }

    @Test
    fun `ignores intents that are not SMS_RECEIVED`() {
        val intent = Intent("android.intent.action.SOME_OTHER_ACTION")

        receiver.onReceive(context, intent)

        verify(exactly = 0) { proManager.recordHighValueAction(any()) }
        assertThat(ScreeningLog.getRecent()).isEmpty()
    }

    @Test
    fun `skips processing when user is not Pro`() {
        isProFlow.value = false
        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)

        receiver.onReceive(context, intent)

        verify(exactly = 0) { proManager.recordHighValueAction(any()) }
        assertThat(ScreeningLog.getRecent()).isEmpty()
    }

    @Test
    fun `processes SMS_RECEIVED when Pro and records each parsed message`() {
        // Two real 3GPP SMS-DELIVER PDUs. They decode under Robolectric's
        // SmsMessage.createFromPdu and exercise SmsReceiver's per-message loop:
        // CallerIdDatabase lookup, ScreeningLog write, and HVA recording.
        val pdu1 = makePdu(originatingNumber = "+16175550100", body = "Hi")
        val pdu2 = makePdu(originatingNumber = "+18559975360", body = "Hi") // Google Fi Voicemail

        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
            putExtra("pdus", arrayOf<Any>(pdu1, pdu2))
            putExtra("format", "3gpp")
        }

        // Sanity: PDUs must decode. If Robolectric returns 0 messages our test fixture is broken.
        val decoded = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        assertThat(decoded).isNotNull()
        assertThat(decoded.size).isAtLeast(1)

        receiver.onReceive(context, intent)

        // One HVA per decoded message that had a non-null originating address.
        val withAddress = decoded.count { it.originatingAddress != null }
        verify(exactly = withAddress) { proManager.recordHighValueAction("sms_protection") }

        val recent = ScreeningLog.getRecent()
        assertThat(recent.size).isEqualTo(withAddress)
        recent.forEach {
            assertThat(it.callType).isEqualTo("SMS")
            assertThat(it.verdict).isEqualTo(SpamVerdict.ALLOW)
        }
        // If both PDUs decoded, the Google-Fi voicemail one resolves via CallerIdDatabase.
        val voicemailEntry = recent.find { it.senderName == "Google Fi Voicemail" }
        if (decoded.any { it.originatingAddress?.contains("8559975360") == true }) {
            assertThat(voicemailEntry).isNotNull()
        }
    }

    /**
     * Build a minimal 3GPP SMS-DELIVER PDU for the given international-format number
     * and a 7-bit ASCII-compatible body. Layout:
     *   SMSC length (0) | TP-MTI (0x04) | addr-len | TOA (0x91) | swapped digits |
     *   PID (0) | DCS (0) | SCTS (7 bytes) | UDL | packed 7-bit body
     */
    private fun makePdu(originatingNumber: String, body: String): ByteArray {
        val digits = originatingNumber.removePrefix("+").filter { it.isDigit() }
        val swapped = swapNibbles(digits)
        val packed = pack7Bit(body)
        val out = mutableListOf<Byte>()
        out.add(0x00) // SMSC length = 0 (no SMSC included)
        out.add(0x04) // TP-MTI = SMS-DELIVER
        out.add(digits.length.toByte()) // address length in digits
        out.add(0x91.toByte()) // TOA: international + ISDN/telephone
        out.addAll(swapped.toList())
        out.add(0x00) // PID
        out.add(0x00) // DCS = 7-bit default alphabet
        // Service centre timestamp (any valid 7 bytes)
        out.addAll(listOf<Byte>(0x32, 0x70, 0x21, 0x51, 0x03, 0x00, 0x40))
        out.add(body.length.toByte()) // UDL in septets
        out.addAll(packed.toList())
        return out.toByteArray()
    }

    private fun swapNibbles(digits: String): ByteArray {
        val padded = if (digits.length % 2 == 1) digits + "F" else digits
        return ByteArray(padded.length / 2) { i ->
            val hi = padded[i * 2].digitToInt(16)
            val lo = if (padded[i * 2 + 1] == 'F') 0xF else padded[i * 2 + 1].digitToInt(16)
            ((lo shl 4) or hi).toByte()
        }
    }

    private fun pack7Bit(text: String): ByteArray {
        val out = mutableListOf<Byte>()
        var buffer = 0
        var bits = 0
        for (c in text) {
            buffer = buffer or ((c.code and 0x7F) shl bits)
            bits += 7
            while (bits >= 8) {
                out.add((buffer and 0xFF).toByte())
                buffer = buffer ushr 8
                bits -= 8
            }
        }
        if (bits > 0) out.add((buffer and 0xFF).toByte())
        return out.toByteArray()
    }

    @After
    fun tearDown() {
        ScreeningLog.clear()
    }
}

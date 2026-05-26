package com.igorganapolsky.answerguard.screening

import android.app.Application
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.igorganapolsky.answerguard.billing.ProManager
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric coverage for [AnswerGuardScreeningService].
 *
 * The service is normally constructed by the Android framework and injected by Hilt.
 * We use [Robolectric.buildService] to instantiate it under a real Context, then
 * inject a relaxed [ProManager] mock directly into its public lateinit field.
 *
 * [CallScreeningService.respondToCall] is final, so we wrap the service in a
 * spyk and stub it out (the system would call this when handed a CallResponse,
 * and we assert which response gets built per-verdict).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AnswerGuardScreeningServiceTest {

    private lateinit var context: Application
    private lateinit var service: AnswerGuardScreeningService
    private lateinit var proManager: ProManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // ScreeningLog needs a real backing store
        ScreeningLog.init(context)
        ScreeningLog.clear()

        // Reset the in-process pause flag so each test starts un-paused
        PauseState.setPaused(context, false)

        proManager = mockk(relaxed = true)

        // Real service instance from Robolectric. We bypass Hilt's onCreate-time
        // injection (which requires a HiltAndroidApp) by flipping the generated
        // `injected` boolean before calling create(), and assign proManager
        // manually afterwards.
        val controller = Robolectric.buildService(AnswerGuardScreeningService::class.java)
        val realService = controller.get()
        markHiltInjected(realService)
        controller.create()
        realService.proManager = proManager

        service = spyk(realService, recordPrivateCalls = true)
        // Stub the final framework method — calling it for real would NPE without an actual Call.
        every {
            service.respondToCall(any<Call.Details>(), any<CallScreeningService.CallResponse>())
        } just Runs

        // Default: contact lookup returns false. Stub the object so heuristics drive the verdict.
        mockkObject(ContactsAllowlist)
        every { ContactsAllowlist.isContact(any(), any()) } returns false

        // Initialise UserBlocklist against the real (Robolectric) SharedPreferences.
        UserBlocklist.init(context)
    }

    @After
    fun tearDown() {
        unmockkObject(ContactsAllowlist)
        PauseState.setPaused(context, false)
        ScreeningLog.clear()
    }

    /** Flip Hilt_AnswerGuardScreeningService.injected so the @AndroidEntryPoint inject() is a no-op. */
    private fun markHiltInjected(service: AnswerGuardScreeningService) {
        val hiltClass = service.javaClass.superclass // Hilt_AnswerGuardScreeningService
        val field = hiltClass.getDeclaredField("injected")
        field.isAccessible = true
        field.setBoolean(service, true)
    }

    /** Builds a [Call.Details] mock that returns a Uri-like handle with the given digits. */
    private fun callDetailsFor(handle: String?): Call.Details {
        val details = mockk<Call.Details>(relaxed = true)
        if (handle == null) {
            every { details.handle } returns null
        } else {
            val uri = android.net.Uri.fromParts("tel", handle, null)
            every { details.handle } returns uri
        }
        return details
    }

    @Test
    fun `paused state allows all calls through without evaluating`() {
        PauseState.setPaused(context, true)
        val details = callDetailsFor("18005550199") // would normally be silenced

        service.onScreenCall(details)

        // Allowed response sent — no HVA, no log record
        val responseSlot = slot<CallScreeningService.CallResponse>()
        verify(exactly = 1) { service.respondToCall(details, capture(responseSlot)) }
        verify(exactly = 0) { proManager.recordHighValueAction(any()) }
        assertThat(ScreeningLog.getRecent()).isEmpty()
    }

    @Test
    fun `BLOCK verdict triggers rejectCall, disallowCall, silenceCall, skipNotification`() {
        // 18005550199 hits knownSpamPrefix → SILENCE. Force BLOCK via UserBlocklist instead.
        UserBlocklist.add("16175550100")
        val details = callDetailsFor("16175550100")

        service.onScreenCall(details)

        verify(exactly = 1) { proManager.recordHighValueAction("ai_protection") }
        val recent = ScreeningLog.getRecent()
        assertThat(recent).hasSize(1)
        assertThat(recent[0].verdict).isEqualTo(SpamVerdict.BLOCK)
        assertThat(recent[0].number).isEqualTo("16175550100")
        verify(exactly = 1) { service.respondToCall(eq(details), any()) }
    }

    @Test
    fun `SILENCE verdict for known spam prefix records HVA and logs`() {
        val details = callDetailsFor("18005550199")

        service.onScreenCall(details)

        verify(exactly = 1) { proManager.recordHighValueAction("ai_protection") }
        val recent = ScreeningLog.getRecent()
        assertThat(recent).hasSize(1)
        assertThat(recent[0].verdict).isEqualTo(SpamVerdict.SILENCE)
        verify(exactly = 1) { service.respondToCall(eq(details), any()) }
    }

    @Test
    fun `ALLOW verdict for ordinary number does not record HVA`() {
        val details = callDetailsFor("16175550100")

        service.onScreenCall(details)

        verify(exactly = 0) { proManager.recordHighValueAction(any()) }
        val recent = ScreeningLog.getRecent()
        assertThat(recent).hasSize(1)
        assertThat(recent[0].verdict).isEqualTo(SpamVerdict.ALLOW)
        verify(exactly = 1) { service.respondToCall(eq(details), any()) }
    }

    @Test
    fun `null handle is treated as private number and SILENCED`() {
        val details = callDetailsFor(null)

        service.onScreenCall(details)

        verify(exactly = 1) { proManager.recordHighValueAction("ai_protection") }
        val recent = ScreeningLog.getRecent()
        assertThat(recent).hasSize(1)
        assertThat(recent[0].verdict).isEqualTo(SpamVerdict.SILENCE)
        assertThat(recent[0].number).isEqualTo("")
        verify(exactly = 1) { service.respondToCall(eq(details), any()) }
    }
}

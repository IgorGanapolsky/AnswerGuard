package com.igorganapolsky.answerguard.screening

import android.content.Context
import android.content.SharedPreferences
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SpamVerdictEngineTest {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private var storedNumbers: Set<String> = emptySet()

    @Before
    fun setUp() {
        context = mockk()
        prefs = mockk()
        editor = mockk()
        storedNumbers = emptySet()

        mockkObject(ContactsAllowlist)
        every { ContactsAllowlist.isContact(any(), any()) } returns false

        every {
            context.getSharedPreferences("answerguard_blocklist", Context.MODE_PRIVATE)
        } returns prefs
        every { prefs.getStringSet("blocked_numbers", emptySet()) } answers { storedNumbers }
        every { prefs.edit() } returns editor
        every { editor.putStringSet("blocked_numbers", any()) } answers {
            storedNumbers = secondArg()
            editor
        }
        every { editor.apply() } just Runs

        val statePrefs = mockk<SharedPreferences>()
        every {
            context.getSharedPreferences("answerguard_state", Context.MODE_PRIVATE)
        } returns statePrefs
        every { statePrefs.getBoolean("contact_identification_enabled", true) } returns true

        UserBlocklist.init(context)
    }

    @After
    fun tearDown() {
        unmockkObject(ContactsAllowlist)
    }

    @Test
    fun `allows normal local number`() {
        assertEquals(SpamVerdict.ALLOW, SpamVerdictEngine.evaluate(context, "16175550100"))
    }

    @Test
    fun `silences blank private number`() {
        assertEquals(SpamVerdict.SILENCE, SpamVerdictEngine.evaluate(context, ""))
    }

    @Test
    fun `silences known spam prefix`() {
        assertEquals(SpamVerdict.SILENCE, SpamVerdictEngine.evaluate(context, "18005550199"))
    }

    @Test
    fun `silences toll-free scam pattern`() {
        assertEquals(SpamVerdict.SILENCE, SpamVerdictEngine.evaluate(context, "18005551234"))
    }

    @Test
    fun `silences 900 premium number`() {
        assertEquals(SpamVerdict.SILENCE, SpamVerdictEngine.evaluate(context, "19005551234"))
    }

    @Test
    fun `silences 900 premium number without country code`() {
        assertEquals(SpamVerdict.SILENCE, SpamVerdictEngine.evaluate(context, "9005551234"))
    }

    @Test
    fun `handles plus-prefixed international format`() {
        // +1 stripped → evaluate digits only
        val result = SpamVerdictEngine.evaluate(context, "+16175550100")
        assertEquals(SpamVerdict.ALLOW, result)
    }

    @Test
    fun `blocks numbers from user blocklist before heuristic allow`() {
        UserBlocklist.add("16175550100")

        assertEquals(SpamVerdict.BLOCK, SpamVerdictEngine.evaluate(context, "+1 (617) 555-0100"))
    }

    @Test
    fun `allows contacts even if they match spam heuristics`() {
        every { ContactsAllowlist.isContact(any(), "18005550199") } returns true

        assertEquals(SpamVerdict.ALLOW, SpamVerdictEngine.evaluate(context, "18005550199"))
    }

    @Test
    fun `allows Google Fi voicemail number explicitly`() {
        assertEquals(SpamVerdict.ALLOW, SpamVerdictEngine.evaluate(context, "+1 855-997-5360"))
    }

    @Test
    fun `allows T-Mobile voicemail number explicitly`() {
        assertEquals(SpamVerdict.ALLOW, SpamVerdictEngine.evaluate(context, "+1 805-637-7243"))
    }

    @Test
    fun `allows Verizon voicemail number explicitly`() {
        assertEquals(SpamVerdict.ALLOW, SpamVerdictEngine.evaluate(context, "+1 866-822-3348"))
    }

    @Test
    fun `allows AT&T voicemail number explicitly`() {
        assertEquals(SpamVerdict.ALLOW, SpamVerdictEngine.evaluate(context, "+1 888-244-6245"))
    }
}

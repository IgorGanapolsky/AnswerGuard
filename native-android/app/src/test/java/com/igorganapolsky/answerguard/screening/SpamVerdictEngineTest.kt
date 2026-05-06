package com.igorganapolsky.answerguard.screening

import android.content.Context
import android.content.SharedPreferences
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
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

        UserBlocklist.init(context)
    }

    @Test
    fun `allows normal local number`() {
        assertEquals(SpamVerdict.ALLOW, SpamVerdictEngine.evaluate("16175550100"))
    }

    @Test
    fun `silences blank private number`() {
        assertEquals(SpamVerdict.SILENCE, SpamVerdictEngine.evaluate(""))
    }

    @Test
    fun `silences known spam prefix`() {
        assertEquals(SpamVerdict.SILENCE, SpamVerdictEngine.evaluate("18005550199"))
    }

    @Test
    fun `silences toll-free scam pattern`() {
        assertEquals(SpamVerdict.SILENCE, SpamVerdictEngine.evaluate("18005551234"))
    }

    @Test
    fun `silences 900 premium number`() {
        assertEquals(SpamVerdict.SILENCE, SpamVerdictEngine.evaluate("19005551234"))
    }

    @Test
    fun `silences 900 premium number without country code`() {
        assertEquals(SpamVerdict.SILENCE, SpamVerdictEngine.evaluate("9005551234"))
    }

    @Test
    fun `handles plus-prefixed international format`() {
        // +1 stripped → evaluate digits only
        val result = SpamVerdictEngine.evaluate("+16175550100")
        assertEquals(SpamVerdict.ALLOW, result)
    }

    @Test
    fun `blocks numbers from user blocklist before heuristic allow`() {
        UserBlocklist.add("16175550100")

        assertEquals(SpamVerdict.BLOCK, SpamVerdictEngine.evaluate("+1 (617) 555-0100"))
    }
}

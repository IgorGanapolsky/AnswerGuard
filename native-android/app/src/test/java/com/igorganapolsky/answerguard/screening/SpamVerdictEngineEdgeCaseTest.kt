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

class SpamVerdictEngineEdgeCaseTest {
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

        UserBlocklist.init(context)
    }

    @After
    fun tearDown() {
        unmockkObject(ContactsAllowlist)
    }

    @Test
    fun `silences when input contains only non-digit characters`() {
        assertEquals(SpamVerdict.SILENCE, SpamVerdictEngine.evaluate(context, "private"))
    }

    @Test
    fun `silences when input contains only punctuation`() {
        assertEquals(SpamVerdict.SILENCE, SpamVerdictEngine.evaluate(context, "()-+"))
    }

    @Test
    fun `silences known 1888 toll-free spam prefix`() {
        assertEquals(SpamVerdict.SILENCE, SpamVerdictEngine.evaluate(context, "18885550100"))
    }

    @Test
    fun `silences known 1202 area code spam prefix`() {
        assertEquals(SpamVerdict.SILENCE, SpamVerdictEngine.evaluate(context, "12025550100"))
    }

    @Test
    fun `silences 877 toll-free scam pattern`() {
        assertEquals(SpamVerdict.SILENCE, SpamVerdictEngine.evaluate(context, "18775551234"))
    }

    @Test
    fun `silences 866 toll-free scam pattern`() {
        assertEquals(SpamVerdict.SILENCE, SpamVerdictEngine.evaluate(context, "18665551234"))
    }

    @Test
    fun `silences 855 toll-free scam pattern`() {
        assertEquals(SpamVerdict.SILENCE, SpamVerdictEngine.evaluate(context, "18555551234"))
    }

    @Test
    fun `silences 844 toll-free scam pattern`() {
        assertEquals(SpamVerdict.SILENCE, SpamVerdictEngine.evaluate(context, "18445551234"))
    }

    @Test
    fun `silences 833 toll-free scam pattern`() {
        assertEquals(SpamVerdict.SILENCE, SpamVerdictEngine.evaluate(context, "18335551234"))
    }

    @Test
    fun `allows short three digit numbers like 911`() {
        // 911 is not a toll-free pattern, no spam prefix matches, no blocklist hit
        assertEquals(SpamVerdict.ALLOW, SpamVerdictEngine.evaluate(context, "911"))
    }

    @Test
    fun `allows when number is formatted with parentheses and dashes`() {
        assertEquals(SpamVerdict.ALLOW, SpamVerdictEngine.evaluate(context, "(617) 555-0100"))
    }

    @Test
    fun `allows when number is formatted with spaces`() {
        assertEquals(SpamVerdict.ALLOW, SpamVerdictEngine.evaluate(context, "617 555 0100"))
    }

    @Test
    fun `silences toll-free pattern even when wrapped in punctuation`() {
        assertEquals(SpamVerdict.SILENCE, SpamVerdictEngine.evaluate(context, "1 (800) 555-1234"))
    }

    @Test
    fun `silences 900 number with plus prefix`() {
        assertEquals(SpamVerdict.SILENCE, SpamVerdictEngine.evaluate(context, "+19005551234"))
    }

    @Test
    fun `blocklist match wins over toll-free spam pattern`() {
        UserBlocklist.add("18005551234")

        assertEquals(SpamVerdict.BLOCK, SpamVerdictEngine.evaluate(context, "18005551234"))
    }

    @Test
    fun `blocklist match wins over normalized formatted number`() {
        UserBlocklist.add("16175550100")

        assertEquals(SpamVerdict.BLOCK, SpamVerdictEngine.evaluate(context, "(617) 555-0100".let { "1$it" }))
    }

    @Test
    fun `allows local seven digit number not in blocklist`() {
        assertEquals(SpamVerdict.ALLOW, SpamVerdictEngine.evaluate(context, "5550100"))
    }
}

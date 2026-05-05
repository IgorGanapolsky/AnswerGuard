package com.igorganapolsky.answerguard.screening

import org.junit.Assert.assertEquals
import org.junit.Test

class SpamVerdictEngineTest {

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
    fun `handles plus-prefixed international format`() {
        // +1 stripped → evaluate digits only
        val result = SpamVerdictEngine.evaluate("+16175550100")
        assertEquals(SpamVerdict.ALLOW, result)
    }
}

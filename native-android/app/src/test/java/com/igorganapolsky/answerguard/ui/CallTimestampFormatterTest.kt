package com.igorganapolsky.answerguard.ui

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import java.util.TimeZone
import org.junit.Test

class CallTimestampFormatterTest {
    @Test
    fun `format includes weekday month day and time`() {
        val formatted = CallTimestampFormatter.format(
            timestampMillis = 1700000000000L,
            locale = Locale.US,
            timeZone = TimeZone.getTimeZone("UTC"),
        )

        assertThat(formatted).isEqualTo("Tue, Nov 14, 10:13 PM")
    }

    @Test
    fun `format uses the supplied timezone`() {
        val formatted = CallTimestampFormatter.format(
            timestampMillis = 1700000000000L,
            locale = Locale.US,
            timeZone = TimeZone.getTimeZone("America/New_York"),
        )

        assertThat(formatted).isEqualTo("Tue, Nov 14, 5:13 PM")
    }
}

package com.igorganapolsky.answerguard.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CallTimestampFormatter {
    fun format(
        timestampMillis: Long,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String {
        return SimpleDateFormat("EEE, MMM d, h:mm a", locale).apply {
            this.timeZone = timeZone
        }.format(Date(timestampMillis))
    }
}

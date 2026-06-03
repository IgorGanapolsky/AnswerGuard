package com.igorganapolsky.answerguard

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.igorganapolsky.answerguard.screening.ScreenedCall
import com.igorganapolsky.answerguard.screening.ScreeningLog
import com.igorganapolsky.answerguard.screening.SpamVerdict
import com.igorganapolsky.answerguard.ui.CallTimestampFormatter
import java.util.Locale
import java.util.TimeZone
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecentActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        ScreeningLog.init(ApplicationProvider.getApplicationContext())
        ScreeningLog.clear()
    }

    @Test
    fun recentActivityShowsWeekdayDateAndTime() {
        val timestamp = 1700000000000L
        ScreeningLog.record(
            ScreenedCall(
                number = "16175550100",
                verdict = SpamVerdict.ALLOW,
                timestamp = timestamp,
            ),
        )

        composeRule.activity.runOnUiThread {
            composeRule.activity.recreate()
        }

        val expectedTimestamp = CallTimestampFormatter.format(
            timestampMillis = timestamp,
            locale = Locale.getDefault(),
            timeZone = TimeZone.getDefault(),
        )

        composeRule
            .onNodeWithText("$expectedTimestamp • Called 1 time (0 blocked, 1 allowed)")
            .performScrollTo()
            .assertExists()
    }
}

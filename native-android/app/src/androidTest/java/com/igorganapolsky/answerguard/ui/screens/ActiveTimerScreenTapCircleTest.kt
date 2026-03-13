package com.igorganapolsky.answerguard.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.igorganapolsky.answerguard.domain.model.TimerConfig
import com.igorganapolsky.answerguard.domain.model.TimerState
import com.igorganapolsky.answerguard.domain.model.TimerStatus
import com.igorganapolsky.answerguard.ui.theme.AnswerGuardTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class ActiveTimerScreenTapCircleTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tappingCircleWhenAlarmCallsOnSilence() {
        val state = TimerState(
            config = TimerConfig.DEFAULT,
            targetDuration = 5.seconds,
            remainingDuration = 0.seconds,
            status = TimerStatus.ALARM,
            alarmTimeRemaining = 10.seconds,
        )

        var silenced = false

        composeRule.setContent {
            AnswerGuardTheme {
                ActiveTimerScreen(
                    state = state,
                    onStop = {},
                    onDismissAlarm = {},
                    onSilence = { silenced = true },
                    onPause = {},
                    onResume = {},
                    onReset = {},
                    onLoopToggle = {},
                )
            }
        }

        // CircularTimer exposes "Timer complete" accessibility label for ALARM/COMPLETE.
        composeRule
            .onNodeWithContentDescription("Timer complete")
            .performTouchInput { click() }

        composeRule.runOnIdle {
            assertTrue(silenced)
        }
    }

    @Test
    fun tappingCircleWhenRunningDoesNothing() {
        val state = TimerState(
            config = TimerConfig.DEFAULT,
            targetDuration = 60.seconds,
            remainingDuration = 30.seconds,
            status = TimerStatus.RUNNING,
        )

        var silenced = false
        var dismissed = false

        composeRule.setContent {
            AnswerGuardTheme {
                ActiveTimerScreen(
                    state = state,
                    onStop = {},
                    onDismissAlarm = { dismissed = true },
                    onSilence = { silenced = true },
                    onPause = {},
                    onResume = {},
                    onReset = {},
                    onLoopToggle = {},
                )
            }
        }

        // RUNNING state shows "Timer running" accessibility label, not "Timer complete"
        // The circle should NOT be clickable during RUNNING state
        composeRule
            .onNodeWithContentDescription("Timer running")
            .performTouchInput { click() }

        composeRule.runOnIdle {
            assertTrue(!silenced)
            assertTrue(!dismissed)
        }
    }

    @Test
    fun tappingCircleWhenCompleteDoesNothing() {
        val state = TimerState(
            config = TimerConfig.DEFAULT,
            targetDuration = 5.seconds,
            remainingDuration = 0.seconds,
            status = TimerStatus.COMPLETE,
        )

        var silenced = false
        var dismissed = false

        composeRule.setContent {
            AnswerGuardTheme {
                ActiveTimerScreen(
                    state = state,
                    onStop = {},
                    onDismissAlarm = { dismissed = true },
                    onSilence = { silenced = true },
                    onPause = {},
                    onResume = {},
                    onReset = {},
                    onLoopToggle = {},
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Timer complete")
            .performTouchInput { click() }

        composeRule.runOnIdle {
            // Circle tap during COMPLETE should NOT trigger silence or dismiss
            assertTrue(!silenced)
            assertTrue(!dismissed)
        }
    }
}

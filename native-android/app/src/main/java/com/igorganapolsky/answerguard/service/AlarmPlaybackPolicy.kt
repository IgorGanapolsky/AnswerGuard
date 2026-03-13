package com.igorganapolsky.answerguard.service

import com.igorganapolsky.answerguard.domain.model.TimerStatus

internal object AlarmPlaybackPolicy {
    fun shouldSilenceOnScreenOff(status: TimerStatus?): Boolean = status == TimerStatus.ALARM

    fun shouldRequestAudioFocus(status: TimerStatus?): Boolean = status == TimerStatus.ALARM
}

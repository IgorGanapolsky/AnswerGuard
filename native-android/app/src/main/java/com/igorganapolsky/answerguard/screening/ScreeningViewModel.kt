package com.igorganapolsky.answerguard.screening

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ScreeningViewModel @Inject constructor() : ViewModel() {

    var blockedNumbers by mutableStateOf(UserBlocklist.getAll())
        private set

    var recentEvents by mutableStateOf(ScreeningLog.getEvents())
        private set

    fun refresh() {
        blockedNumbers = UserBlocklist.getAll()
        recentEvents = ScreeningLog.getEvents()
    }

    fun addBlockedNumber(number: String) {
        val digits = number.filter { it.isDigit() }
        if (digits.isNotBlank()) {
            UserBlocklist.add(digits)
            refresh()
        }
    }

    fun removeBlockedNumber(number: String) {
        UserBlocklist.remove(number)
        refresh()
    }
}

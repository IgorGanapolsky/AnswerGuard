package com.igorganapolsky.answerguard.domain.repository

import com.igorganapolsky.answerguard.domain.model.TimerConfig
import com.igorganapolsky.answerguard.domain.model.TimerState
import kotlinx.coroutines.flow.Flow

/**
 * Repository for timer configuration and state persistence.
 */
interface TimerRepository {
    /**
     * Get the saved timer configuration.
     */
    fun getTimerConfig(): Flow<TimerConfig>

    /**
     * Save timer configuration for persistence.
     */
    suspend fun saveTimerConfig(config: TimerConfig)

    /**
     * Get the current active timer state, if any.
     */
    fun getActiveTimer(): Flow<TimerState?>

    /**
     * Save the active timer state for recovery after app restart.
     */
    suspend fun saveActiveTimer(state: TimerState)

    /**
     * Clear the active timer (when complete or cancelled).
     */
    suspend fun clearActiveTimer()
}

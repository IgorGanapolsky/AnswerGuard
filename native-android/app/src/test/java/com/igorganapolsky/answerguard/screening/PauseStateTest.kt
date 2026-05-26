package com.igorganapolsky.answerguard.screening

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class PauseStateTest {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private var stored: Boolean = false

    @Before
    fun setUp() {
        context = mockk()
        prefs = mockk()
        editor = mockk()
        stored = false

        every {
            context.getSharedPreferences("answerguard_state", Context.MODE_PRIVATE)
        } returns prefs
        every { prefs.getBoolean("screening_paused", false) } answers { stored }
        every { prefs.edit() } returns editor
        every { editor.putBoolean("screening_paused", any()) } answers {
            stored = secondArg()
            editor
        }
        every { editor.apply() } just Runs
    }

    @Test
    fun `isPaused is false by default`() {
        assertThat(PauseState.isPaused(context)).isFalse()
    }

    @Test
    fun `setPaused true makes isPaused return true`() {
        PauseState.setPaused(context, true)

        assertThat(PauseState.isPaused(context)).isTrue()
    }

    @Test
    fun `setPaused false makes isPaused return false`() {
        PauseState.setPaused(context, true)
        PauseState.setPaused(context, false)

        assertThat(PauseState.isPaused(context)).isFalse()
    }

    @Test
    fun `setPaused writes through to SharedPreferences editor`() {
        PauseState.setPaused(context, true)

        verify { editor.putBoolean("screening_paused", true) }
        verify { editor.apply() }
    }

    @Test
    fun `isPaused reads from the answerguard_state preferences file`() {
        PauseState.isPaused(context)

        verify { context.getSharedPreferences("answerguard_state", Context.MODE_PRIVATE) }
    }
}

package com.igorganapolsky.answerguard.screening

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

class UserBlocklistTest {
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

    @Test
    fun `getAll is empty after initialization with no numbers`() {
        assertThat(UserBlocklist.getAll()).isEmpty()
    }

    @Test
    fun `add stores number and contains can read it`() {
        UserBlocklist.add("16175550123")

        assertThat(UserBlocklist.contains("16175550123")).isTrue()
        assertThat(UserBlocklist.getAll()).containsExactly("16175550123")
    }

    @Test
    fun `remove deletes only requested number`() {
        UserBlocklist.add("16175550123")
        UserBlocklist.add("16175550124")

        UserBlocklist.remove("16175550123")

        assertThat(UserBlocklist.contains("16175550123")).isFalse()
        assertThat(UserBlocklist.contains("16175550124")).isTrue()
    }
}

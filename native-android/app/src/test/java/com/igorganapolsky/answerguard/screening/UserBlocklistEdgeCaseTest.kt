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

class UserBlocklistEdgeCaseTest {
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
    fun `adding the same number twice leaves only one entry`() {
        UserBlocklist.add("16175550111")
        UserBlocklist.add("16175550111")

        assertThat(UserBlocklist.getAll()).containsExactly("16175550111")
    }

    @Test
    fun `removing a number that was never added is a no-op`() {
        UserBlocklist.add("16175550111")

        UserBlocklist.remove("19998887777")

        assertThat(UserBlocklist.getAll()).containsExactly("16175550111")
        assertThat(UserBlocklist.contains("19998887777")).isFalse()
    }

    @Test
    fun `contains is false for any number when blocklist is empty`() {
        assertThat(UserBlocklist.contains("16175550111")).isFalse()
    }

    @Test
    fun `add stores multiple distinct numbers`() {
        UserBlocklist.add("16175550111")
        UserBlocklist.add("16175550112")
        UserBlocklist.add("16175550113")

        assertThat(UserBlocklist.getAll())
            .containsExactly("16175550111", "16175550112", "16175550113")
    }

    @Test
    fun `remove of last entry leaves blocklist empty`() {
        UserBlocklist.add("16175550111")

        UserBlocklist.remove("16175550111")

        assertThat(UserBlocklist.getAll()).isEmpty()
    }

    @Test
    fun `getAll returns the persisted snapshot from prefs`() {
        UserBlocklist.add("16175550111")
        UserBlocklist.add("16175550112")

        val all = UserBlocklist.getAll()

        assertThat(all).hasSize(2)
    }

    @Test
    fun `treats numbers as opaque strings - distinct formats are different entries`() {
        UserBlocklist.add("16175550111")
        UserBlocklist.add("+16175550111")

        assertThat(UserBlocklist.getAll()).hasSize(2)
        assertThat(UserBlocklist.contains("16175550111")).isTrue()
        assertThat(UserBlocklist.contains("+16175550111")).isTrue()
    }
}

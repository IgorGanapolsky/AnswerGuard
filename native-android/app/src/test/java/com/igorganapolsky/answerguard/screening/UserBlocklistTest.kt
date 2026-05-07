package com.igorganapolsky.answerguard.screening

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UserBlocklistTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)

        every { mockContext.getSharedPreferences("answerguard_blocklist", Context.MODE_PRIVATE) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putStringSet(any(), any()) } returns mockEditor

        UserBlocklist.init(mockContext)
    }

    @Test
    fun `add adds to existing blocklist`() {
        every { mockPrefs.getStringSet("blocked_numbers", any()) } returns setOf("123")
        UserBlocklist.add("456")
        verify { mockEditor.putStringSet("blocked_numbers", setOf("123", "456")) }
        verify { mockEditor.apply() }
    }

    @Test
    fun `remove removes from blocklist`() {
        every { mockPrefs.getStringSet("blocked_numbers", any()) } returns setOf("123", "456")
        UserBlocklist.remove("123")
        verify { mockEditor.putStringSet("blocked_numbers", setOf("456")) }
        verify { mockEditor.apply() }
    }

    @Test
    fun `contains returns true if present`() {
        every { mockPrefs.getStringSet("blocked_numbers", any()) } returns setOf("123")
        assertTrue(UserBlocklist.contains("123"))
        assertFalse(UserBlocklist.contains("456"))
    }
    
    @Test
    fun `getAll returns empty when uninitialized or empty`() {
        every { mockPrefs.getStringSet("blocked_numbers", any()) } returns emptySet()
        assertTrue(UserBlocklist.getAll().isEmpty())
    }
}

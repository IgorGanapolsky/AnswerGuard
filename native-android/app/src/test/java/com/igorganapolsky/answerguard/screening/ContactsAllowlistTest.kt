package com.igorganapolsky.answerguard.screening

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import androidx.core.content.ContextCompat
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Test

class ContactsAllowlistTest {
    private lateinit var context: Context
    private lateinit var resolver: ContentResolver

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        resolver = mockk()
        every { context.contentResolver } returns resolver

        mockkStatic(ContextCompat::class)
        mockkStatic(Uri::class)
        // Stub Uri.encode and Uri.withAppendedPath so production code can build a URI
        // without touching the real Android framework classes.
        every { Uri.encode(any<String>()) } answers { firstArg<String>() }
        val fakeUri = mockk<Uri>(relaxed = true)
        every { Uri.withAppendedPath(any(), any()) } returns fakeUri
    }

    @After
    fun tearDown() {
        unmockkStatic(ContextCompat::class)
        unmockkStatic(Uri::class)
    }

    @Test
    fun `returns false for blank digits without checking permission`() {
        assertThat(ContactsAllowlist.isContact(context, "")).isFalse()
    }

    @Test
    fun `returns false when READ_CONTACTS permission is not granted`() {
        every {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)
        } returns PackageManager.PERMISSION_DENIED

        assertThat(ContactsAllowlist.isContact(context, "16175550100")).isFalse()
    }

    @Test
    fun `returns true when permission granted and cursor finds a match`() {
        every {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)
        } returns PackageManager.PERMISSION_GRANTED

        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns true
        every { cursor.getString(0) } returns "Alice"
        every { cursor.close() } returns Unit
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor

        assertThat(ContactsAllowlist.isContact(context, "16175550100")).isTrue()
    }

    @Test
    fun `returns false when permission granted but cursor is empty`() {
        every {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)
        } returns PackageManager.PERMISSION_GRANTED

        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns false
        every { cursor.close() } returns Unit
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursor

        assertThat(ContactsAllowlist.isContact(context, "16175550100")).isFalse()
    }

    @Test
    fun `returns false when permission granted but resolver returns null cursor`() {
        every {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)
        } returns PackageManager.PERMISSION_GRANTED
        every { resolver.query(any(), any(), any(), any(), any()) } returns null

        assertThat(ContactsAllowlist.isContact(context, "16175550100")).isFalse()
    }

    @Test
    fun `returns false when resolver query throws`() {
        every {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)
        } returns PackageManager.PERMISSION_GRANTED
        every { resolver.query(any(), any(), any(), any(), any()) } throws SecurityException("denied")

        assertThat(ContactsAllowlist.isContact(context, "16175550100")).isFalse()
    }
}

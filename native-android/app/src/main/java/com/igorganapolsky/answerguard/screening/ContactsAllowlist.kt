package com.igorganapolsky.answerguard.screening

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log

/**
 * Checks system contacts to see if a phone number is already known.
 * Requires [android.permission.READ_CONTACTS].
 */
object ContactsAllowlist {

    private val tag = "ContactsAllowlist"

    fun isContact(context: Context, digits: String): Boolean {
        if (digits.isBlank()) return false

        // Check permission explicitly
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_CONTACTS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(tag, "READ_CONTACTS permission not granted; skipping contact check")
            return false
        }
        
        // We use PhoneLookup URI which is optimized for matching phone numbers
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(digits)
        )
        
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val found = cursor.moveToFirst()
                if (found) {
                    val name = cursor.getString(0)
                    Log.d(tag, "Number matched contact: $name")
                }
                found
            } ?: false
        } catch (e: Exception) {
            Log.e(tag, "Error querying contacts", e)
            false
        }
    }
}

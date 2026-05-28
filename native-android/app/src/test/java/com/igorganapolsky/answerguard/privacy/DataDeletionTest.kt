package com.igorganapolsky.answerguard.privacy

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies [DataDeletion.deleteAllUserData] actually wipes every
 * SharedPreferences file the app writes — the Play Data Safety
 * user-initiated-deletion contract depends on this being complete.
 *
 * Also acts as a tripwire: if a new SharedPreferences store ships without
 * being added to [DataDeletion.PREFS_FILES], the
 * `deletes_every_known_prefs_file` test still passes for that file, but
 * the runtime audit grep in CI will catch the missing entry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DataDeletionTest {

    @Test
    fun `deleteAllUserData clears every known prefs file`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        // Seed each store with something so we can detect non-clears.
        for (name in DataDeletion.PREFS_FILES) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit()
                .putString("sentinel", "value")
                .putInt("counter", 42)
                .commit()
        }

        val cleared = DataDeletion.deleteAllUserData(context)

        assertThat(cleared).isEqualTo(DataDeletion.PREFS_FILES.size)
        for (name in DataDeletion.PREFS_FILES) {
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            assertThat(prefs.getString("sentinel", null)).isNull()
            assertThat(prefs.getInt("counter", -1)).isEqualTo(-1)
            assertThat(prefs.all).isEmpty()
        }
    }

    @Test
    fun `deleteAllUserData is idempotent on an empty install`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val cleared = DataDeletion.deleteAllUserData(context)
        assertThat(cleared).isEqualTo(DataDeletion.PREFS_FILES.size)
        // Second pass should still be safe.
        val clearedAgain = DataDeletion.deleteAllUserData(context)
        assertThat(clearedAgain).isEqualTo(DataDeletion.PREFS_FILES.size)
    }

    @Test
    fun `PREFS_FILES list has no duplicates`() {
        val seen = DataDeletion.PREFS_FILES.toSet()
        assertThat(seen).hasSize(DataDeletion.PREFS_FILES.size)
    }

    @Test
    fun `PREFS_FILES list covers all known stores`() {
        // Hard-coded snapshot of what the app currently writes. If you add
        // a new getSharedPreferences(...) call, add the file name here too.
        val expected = setOf(
            "answerguard_screening_log",
            "answerguard_blocklist",
            "answerguard_state",
            "answerguard_prompts",
            "answerguard_analytics",
            "review_prefs",
            "monetization_prefs",
            "pro_prefs",
        )
        assertThat(DataDeletion.PREFS_FILES.toSet()).isEqualTo(expected)
    }
}

package com.igorganapolsky.answerguard.screening

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SeenSeeAllCallsPromptTest {

    private lateinit var context: Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SeenSeeAllCallsPrompt.reset(context)
    }

    @After
    fun tearDown() {
        SeenSeeAllCallsPrompt.reset(context)
    }

    @Test
    fun `wasShown is false on a fresh install`() {
        assertThat(SeenSeeAllCallsPrompt.wasShown(context)).isFalse()
    }

    @Test
    fun `markShown flips wasShown to true`() {
        SeenSeeAllCallsPrompt.markShown(context)
        assertThat(SeenSeeAllCallsPrompt.wasShown(context)).isTrue()
    }

    @Test
    fun `markShown is idempotent`() {
        SeenSeeAllCallsPrompt.markShown(context)
        SeenSeeAllCallsPrompt.markShown(context)
        assertThat(SeenSeeAllCallsPrompt.wasShown(context)).isTrue()
    }

    @Test
    fun `reset clears the flag`() {
        SeenSeeAllCallsPrompt.markShown(context)
        SeenSeeAllCallsPrompt.reset(context)
        assertThat(SeenSeeAllCallsPrompt.wasShown(context)).isFalse()
    }
}

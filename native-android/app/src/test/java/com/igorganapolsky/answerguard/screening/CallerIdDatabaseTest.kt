package com.igorganapolsky.answerguard.screening

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CallerIdDatabaseTest {

    @Test
    fun `identifies Google Fi voicemail by digits-only`() {
        assertThat(CallerIdDatabase.identify("18559975360"))
            .isEqualTo("Google Fi Voicemail")
    }

    @Test
    fun `identifies Google Fi voicemail without leading 1`() {
        assertThat(CallerIdDatabase.identify("8559975360"))
            .isEqualTo("Google Fi Voicemail")
    }

    @Test
    fun `identifies Google Fi voicemail when formatted with punctuation`() {
        assertThat(CallerIdDatabase.identify("+1 (855) 997-5360"))
            .isEqualTo("Google Fi Voicemail")
    }

    @Test
    fun `identifies T-Mobile voicemail with country code`() {
        assertThat(CallerIdDatabase.identify("18056377243"))
            .isEqualTo("T-Mobile Voicemail")
    }

    @Test
    fun `identifies T-Mobile voicemail without country code`() {
        assertThat(CallerIdDatabase.identify("8056377243"))
            .isEqualTo("T-Mobile Voicemail")
    }

    @Test
    fun `identifies Verizon voicemail with country code`() {
        assertThat(CallerIdDatabase.identify("18668223348"))
            .isEqualTo("Verizon Voicemail Retrieval")
    }

    @Test
    fun `identifies Verizon voicemail without country code`() {
        assertThat(CallerIdDatabase.identify("8668223348"))
            .isEqualTo("Verizon Voicemail Retrieval")
    }

    @Test
    fun `identifies ATT voicemail with country code`() {
        assertThat(CallerIdDatabase.identify("18882446245"))
            .isEqualTo("AT&T Voicemail Retrieval")
    }

    @Test
    fun `identifies ATT voicemail without country code`() {
        assertThat(CallerIdDatabase.identify("8882446245"))
            .isEqualTo("AT&T Voicemail Retrieval")
    }

    @Test
    fun `identifies ABC Puerto Rico with country code`() {
        assertThat(CallerIdDatabase.identify("19544483475"))
            .isEqualTo("ABC Puerto Rico")
    }

    @Test
    fun `identifies ABC Puerto Rico without country code`() {
        assertThat(CallerIdDatabase.identify("9544483475"))
            .isEqualTo("ABC Puerto Rico")
    }

    @Test
    fun `identifies unsubscribe campaign with country code`() {
        assertThat(CallerIdDatabase.identify("18706888127"))
            .isEqualTo("Unsubscribe Campaign")
    }

    @Test
    fun `identifies unsubscribe campaign without country code`() {
        assertThat(CallerIdDatabase.identify("8706888127"))
            .isEqualTo("Unsubscribe Campaign")
    }

    @Test
    fun `returns null for unknown number`() {
        assertThat(CallerIdDatabase.identify("16175550100")).isNull()
    }

    @Test
    fun `returns null for empty string`() {
        assertThat(CallerIdDatabase.identify("")).isNull()
    }

    @Test
    fun `returns null when input has no digits`() {
        assertThat(CallerIdDatabase.identify("private")).isNull()
    }

    @Test
    fun `returns null when input has only punctuation`() {
        assertThat(CallerIdDatabase.identify("()-+ ")).isNull()
    }
}

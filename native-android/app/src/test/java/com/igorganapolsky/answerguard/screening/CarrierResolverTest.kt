package com.igorganapolsky.answerguard.screening

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CarrierResolverTest {

    @Test
    fun `resolves Google Fi voicemail with country code prefix`() {
        assertThat(CarrierResolver.resolve("+18559975360")).isEqualTo("Google Fi")
    }

    @Test
    fun `resolves Google Fi voicemail without country code prefix`() {
        assertThat(CarrierResolver.resolve("8559975360")).isEqualTo("Google Fi")
    }

    @Test
    fun `resolves Google Fi voicemail when formatted with punctuation`() {
        assertThat(CarrierResolver.resolve("+1 (855) 997-5360")).isEqualTo("Google Fi")
    }

    @Test
    fun `resolves T-Mobile voicemail with country code prefix`() {
        assertThat(CarrierResolver.resolve("18056377243")).isEqualTo("T-Mobile")
    }

    @Test
    fun `resolves T-Mobile voicemail without country code prefix`() {
        assertThat(CarrierResolver.resolve("8056377243")).isEqualTo("T-Mobile")
    }

    @Test
    fun `resolves Verizon voicemail with country code prefix`() {
        assertThat(CarrierResolver.resolve("18668223348")).isEqualTo("Verizon")
    }

    @Test
    fun `resolves Verizon voicemail without country code prefix`() {
        assertThat(CarrierResolver.resolve("8668223348")).isEqualTo("Verizon")
    }

    @Test
    fun `resolves ATT voicemail with country code prefix`() {
        assertThat(CarrierResolver.resolve("18882446245")).isEqualTo("AT&T")
    }

    @Test
    fun `resolves ATT voicemail without country code prefix`() {
        assertThat(CarrierResolver.resolve("8882446245")).isEqualTo("AT&T")
    }

    @Test
    fun `resolves secondary ATT number with country code prefix`() {
        assertThat(CarrierResolver.resolve("19544483475")).isEqualTo("AT&T")
    }

    @Test
    fun `resolves secondary ATT number without country code prefix`() {
        assertThat(CarrierResolver.resolve("9544483475")).isEqualTo("AT&T")
    }

    @Test
    fun `resolves secondary Verizon number with country code prefix`() {
        assertThat(CarrierResolver.resolve("14082560351")).isEqualTo("Verizon")
    }

    @Test
    fun `resolves secondary Verizon number without country code prefix`() {
        assertThat(CarrierResolver.resolve("4082560351")).isEqualTo("Verizon")
    }

    @Test
    fun `resolves tertiary ATT number with country code prefix`() {
        assertThat(CarrierResolver.resolve("18884021096")).isEqualTo("AT&T")
    }

    @Test
    fun `resolves tertiary ATT number without country code prefix`() {
        assertThat(CarrierResolver.resolve("8884021096")).isEqualTo("AT&T")
    }

    @Test
    fun `resolves secondary T-Mobile number with country code prefix`() {
        assertThat(CarrierResolver.resolve("19544940469")).isEqualTo("T-Mobile")
    }

    @Test
    fun `resolves secondary T-Mobile number without country code prefix`() {
        assertThat(CarrierResolver.resolve("9544940469")).isEqualTo("T-Mobile")
    }

    @Test
    fun `resolves tertiary Verizon number with country code prefix`() {
        assertThat(CarrierResolver.resolve("18706888127")).isEqualTo("Verizon")
    }

    @Test
    fun `resolves tertiary Verizon number without country code prefix`() {
        assertThat(CarrierResolver.resolve("8706888127")).isEqualTo("Verizon")
    }

    @Test
    fun `returns null for unknown number`() {
        assertThat(CarrierResolver.resolve("16175550100")).isNull()
    }

    @Test
    fun `returns null for empty string`() {
        assertThat(CarrierResolver.resolve("")).isNull()
    }

    @Test
    fun `returns null when input has no digits`() {
        assertThat(CarrierResolver.resolve("private")).isNull()
    }

    @Test
    fun `returns null when input has only punctuation`() {
        assertThat(CarrierResolver.resolve("()-+ ")).isNull()
    }
}

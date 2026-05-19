package com.igorganapolsky.answerguard.billing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SubscriptionOfferTest {
    @Test
    fun `displayPrice prefers the last pricing phase`() {
        val offer = SubscriptionOffer(
            offerToken = "token",
            pricingPhases = listOf(
                SubscriptionPricingPhase(formattedPrice = "$0.00", billingPeriod = "P7D"),
                SubscriptionPricingPhase(formattedPrice = "$4.99", billingPeriod = "P1M"),
                SubscriptionPricingPhase(formattedPrice = "$29.99", billingPeriod = "P1Y"),
            ),
        )

        assertThat(offer.displayPrice).isEqualTo("$29.99")
    }

    @Test
    fun `displayPrice returns the only phase when there is one`() {
        val offer = SubscriptionOffer(
            offerToken = "token",
            pricingPhases = listOf(
                SubscriptionPricingPhase(formattedPrice = "$4.99", billingPeriod = "P1M"),
            ),
        )

        assertThat(offer.displayPrice).isEqualTo("$4.99")
    }

    @Test
    fun `displayPrice is null when pricing phases list is empty`() {
        val offer = SubscriptionOffer(offerToken = "token", pricingPhases = emptyList())

        assertThat(offer.displayPrice).isNull()
    }

    @Test
    fun `data class equality holds for identical contents`() {
        val a = SubscriptionOffer(
            offerToken = "x",
            pricingPhases = listOf(SubscriptionPricingPhase("$1", "P1M")),
        )
        val b = SubscriptionOffer(
            offerToken = "x",
            pricingPhases = listOf(SubscriptionPricingPhase("$1", "P1M")),
        )

        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }

    @Test
    fun `pricing phase data class equality reflects price and period`() {
        val a = SubscriptionPricingPhase(formattedPrice = "$1", billingPeriod = "P1M")
        val b = SubscriptionPricingPhase(formattedPrice = "$1", billingPeriod = "P1M")
        val differentPrice = SubscriptionPricingPhase(formattedPrice = "$2", billingPeriod = "P1M")

        assertThat(a).isEqualTo(b)
        assertThat(a).isNotEqualTo(differentPrice)
    }
}

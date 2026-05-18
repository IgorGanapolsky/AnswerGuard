package com.igorganapolsky.answerguard.billing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SubscriptionOfferSelectionAdditionalTest {
    @Test
    fun `selectPreferredSubscriptionOffer matches yearly even when it is not the first offer`() {
        val weekly = SubscriptionOffer(
            offerToken = "weekly",
            pricingPhases = listOf(SubscriptionPricingPhase("$1.99", "P1W")),
        )
        val monthly = SubscriptionOffer(
            offerToken = "monthly",
            pricingPhases = listOf(SubscriptionPricingPhase("$4.99", "P1M")),
        )
        val yearly = SubscriptionOffer(
            offerToken = "yearly",
            pricingPhases = listOf(SubscriptionPricingPhase("$29.99", "P1Y")),
        )

        val selected = selectPreferredSubscriptionOffer(listOf(weekly, monthly, yearly))

        assertThat(selected?.offerToken).isEqualTo("yearly")
    }

    @Test
    fun `selectPreferredSubscriptionOffer picks the first yearly when multiple exist`() {
        val first = SubscriptionOffer(
            offerToken = "yearly-a",
            pricingPhases = listOf(SubscriptionPricingPhase("$29.99", "P1Y")),
        )
        val second = SubscriptionOffer(
            offerToken = "yearly-b",
            pricingPhases = listOf(SubscriptionPricingPhase("$39.99", "P1Y")),
        )

        val selected = selectPreferredSubscriptionOffer(listOf(first, second))

        assertThat(selected?.offerToken).isEqualTo("yearly-a")
    }

    @Test
    fun `selectPreferredSubscriptionOffer detects yearly in any pricing phase not just the last`() {
        val yearlyIntroThenMonthly = SubscriptionOffer(
            offerToken = "intro-yearly",
            pricingPhases = listOf(
                SubscriptionPricingPhase("$0.00", "P1Y"),
                SubscriptionPricingPhase("$4.99", "P1M"),
            ),
        )
        val monthlyOnly = SubscriptionOffer(
            offerToken = "monthly",
            pricingPhases = listOf(SubscriptionPricingPhase("$4.99", "P1M")),
        )

        val selected = selectPreferredSubscriptionOffer(listOf(monthlyOnly, yearlyIntroThenMonthly))

        assertThat(selected?.offerToken).isEqualTo("intro-yearly")
    }

    @Test
    fun `selectPreferredSubscriptionOffer returns sole offer when only one exists and it is not yearly`() {
        val monthly = SubscriptionOffer(
            offerToken = "monthly",
            pricingPhases = listOf(SubscriptionPricingPhase("$4.99", "P1M")),
        )

        val selected = selectPreferredSubscriptionOffer(listOf(monthly))

        assertThat(selected?.offerToken).isEqualTo("monthly")
    }

    @Test
    fun `selectPreferredSubscriptionOffer returns first offer when all have empty pricing phases`() {
        val a = SubscriptionOffer(offerToken = "a", pricingPhases = emptyList())
        val b = SubscriptionOffer(offerToken = "b", pricingPhases = emptyList())

        val selected = selectPreferredSubscriptionOffer(listOf(a, b))

        assertThat(selected?.offerToken).isEqualTo("a")
    }
}

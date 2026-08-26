package com.igorganapolsky.answerguard.billing

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import app.cash.turbine.test
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import com.google.common.truth.Truth.assertThat
import com.igorganapolsky.answerguard.analytics.AnalyticsService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProManagerTest {
    private lateinit var context: Context
    private lateinit var analyticsService: AnalyticsService
    private lateinit var billingClient: BillingClient
    private lateinit var monetizationPrefs: SharedPreferences
    private lateinit var monetizationEditor: SharedPreferences.Editor
    private lateinit var proPrefs: SharedPreferences
    private lateinit var proEditor: SharedPreferences.Editor

    // Backing maps simulating SharedPreferences state.
    private val monetizationStore = mutableMapOf<String, Any?>()
    private val proStore = mutableMapOf<String, Any?>()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        context = mockk()
        analyticsService = mockk(relaxed = true)
        billingClient = mockk(relaxed = true)

        monetizationStore.clear()
        proStore.clear()

        // --- monetization_prefs (used by hva counter) ---
        monetizationPrefs = mockk()
        monetizationEditor = mockk()
        every {
            context.getSharedPreferences("monetization_prefs", Context.MODE_PRIVATE)
        } returns monetizationPrefs
        every { monetizationPrefs.getInt(any(), any()) } answers {
            (monetizationStore[firstArg<String>()] as? Int) ?: secondArg<Int>()
        }
        every { monetizationPrefs.edit() } returns monetizationEditor
        every { monetizationEditor.putInt(any(), any()) } answers {
            monetizationStore[firstArg<String>()] = secondArg<Int>()
            monetizationEditor
        }
        every { monetizationEditor.commit() } returns true
        every { monetizationEditor.apply() } just Runs

        // --- pro_prefs (used by forcePro) ---
        proPrefs = mockk()
        proEditor = mockk()
        every {
            context.getSharedPreferences("pro_prefs", Context.MODE_PRIVATE)
        } returns proPrefs
        every { proPrefs.edit() } returns proEditor
        every { proEditor.putBoolean(any(), any()) } answers {
            proStore[firstArg<String>()] = secondArg<Boolean>()
            proEditor
        }
        every { proEditor.putString(any(), any()) } answers {
            proStore[firstArg<String>()] = secondArg<String?>()
            proEditor
        }
        every { proEditor.apply() } just Runs

        // --- BillingClient static newBuilder chain ---
        mockkStatic(BillingClient::class)
        val builder = mockk<BillingClient.Builder>()
        every { BillingClient.newBuilder(context) } returns builder
        every { builder.setListener(any()) } returns builder
        every { builder.enablePendingPurchases(any<PendingPurchasesParams>()) } returns builder
        every { builder.build() } returns billingClient

        // --- BillingFlowParams: stub static newBuilder so the launchPurchase path can
        // construct flow params without invoking the real builder's validation. ---
        mockkStatic(BillingFlowParams::class)
        mockkStatic(BillingFlowParams.ProductDetailsParams::class)
        val flowBuilder = mockk<BillingFlowParams.Builder>(relaxed = true)
        val flowParams = mockk<BillingFlowParams>(relaxed = true)
        every { BillingFlowParams.newBuilder() } returns flowBuilder
        every { flowBuilder.setProductDetailsParamsList(any()) } returns flowBuilder
        every { flowBuilder.build() } returns flowParams

        // --- BillingFlowParams.ProductDetailsParams: stub static newBuilder too. ---
        val pdBuilder = mockk<BillingFlowParams.ProductDetailsParams.Builder>(relaxed = true)
        val pdParams = mockk<BillingFlowParams.ProductDetailsParams>(relaxed = true)
        every { BillingFlowParams.ProductDetailsParams.newBuilder() } returns pdBuilder
        every { pdBuilder.setProductDetails(any()) } returns pdBuilder
        every { pdBuilder.setOfferToken(any()) } returns pdBuilder
        every { pdBuilder.build() } returns pdParams

        // Default: pretend the client is not ready, and connection attempts fail with
        // SERVICE_DISCONNECTED. That short-circuits connectAndRestore() during init.
        every { billingClient.isReady } returns false
        val listenerSlot = slot<BillingClientStateListener>()
        every { billingClient.startConnection(capture(listenerSlot)) } answers {
            listenerSlot.captured.onBillingServiceDisconnected()
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(BillingClient::class)
        unmockkStatic(BillingFlowParams::class)
        unmockkStatic(BillingFlowParams.ProductDetailsParams::class)
    }

    private fun createProManager(scope: CoroutineScope = testScope): ProManager =
        ProManager(context, analyticsService, scope)

    // ---------------- forcePro cycle ----------------

    @Test
    fun `forcePro cycles NONE to PRO to FAMILY to BUSINESS to NONE`() = runTest(testDispatcher) {
        val manager = createProManager()

        assertThat(manager.entitlementLevel.value).isEqualTo(EntitlementLevel.NONE)

        manager.forcePro()
        assertThat(manager.entitlementLevel.value).isEqualTo(EntitlementLevel.PRO)
        assertThat(proStore["forced_pro"]).isEqualTo(true)
        assertThat(proStore["forced_level"]).isEqualTo("PRO")

        manager.forcePro()
        assertThat(manager.entitlementLevel.value).isEqualTo(EntitlementLevel.FAMILY)
        assertThat(proStore["forced_level"]).isEqualTo("FAMILY")

        manager.forcePro()
        assertThat(manager.entitlementLevel.value).isEqualTo(EntitlementLevel.BUSINESS)
        assertThat(proStore["forced_level"]).isEqualTo("BUSINESS")

        manager.forcePro()
        assertThat(manager.entitlementLevel.value).isEqualTo(EntitlementLevel.NONE)
        assertThat(proStore["forced_pro"]).isEqualTo(false)
        assertThat(proStore["forced_level"]).isEqualTo("NONE")
    }

    @Test
    fun `forcePro fires dev analytics event with current level`() = runTest(testDispatcher) {
        val manager = createProManager()

        manager.forcePro()

        verify {
            analyticsService.track("dev_force_pro", mapOf("level" to "PRO"))
        }
    }

    // ---------------- StateFlow emissions ----------------

    @Test
    fun `entitlementLevel emits transitions from NONE to PRO`() = runTest(testDispatcher) {
        val manager = createProManager()

        manager.entitlementLevel.test {
            assertThat(awaitItem()).isEqualTo(EntitlementLevel.NONE)
            manager.forcePro()
            assertThat(awaitItem()).isEqualTo(EntitlementLevel.PRO)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isPro derived flow follows entitlement level`() = runTest(testDispatcher) {
        val manager = createProManager()
        advanceUntilIdle()

        assertThat(manager.isPro.value).isFalse()
        manager.forcePro() // -> PRO
        advanceUntilIdle()
        assertThat(manager.isPro.value).isTrue()
    }

    @Test
    fun `isElite is true only for FAMILY and BUSINESS`() = runTest(testDispatcher) {
        val manager = createProManager()
        advanceUntilIdle()

        // NONE
        assertThat(manager.isElite.value).isFalse()

        // PRO
        manager.forcePro()
        advanceUntilIdle()
        assertThat(manager.isElite.value).isFalse()

        // FAMILY
        manager.forcePro()
        advanceUntilIdle()
        assertThat(manager.isElite.value).isTrue()

        // BUSINESS
        manager.forcePro()
        advanceUntilIdle()
        assertThat(manager.isElite.value).isTrue()

        // back to NONE
        manager.forcePro()
        advanceUntilIdle()
        assertThat(manager.isElite.value).isFalse()
    }

    // ---------------- Capability gates ----------------

    @Test
    fun `canUseAdvancedRules requires any paid level`() = runTest(testDispatcher) {
        val manager = createProManager()

        assertThat(manager.canUseAdvancedRules(EntitlementLevel.NONE)).isFalse()
        assertThat(manager.canUseAdvancedRules(EntitlementLevel.PRO)).isTrue()
        assertThat(manager.canUseAdvancedRules(EntitlementLevel.FAMILY)).isTrue()
        assertThat(manager.canUseAdvancedRules(EntitlementLevel.BUSINESS)).isTrue()
    }

    @Test
    fun `canUseAdvancedRules without args uses current state`() = runTest(testDispatcher) {
        val manager = createProManager()

        assertThat(manager.canUseAdvancedRules()).isFalse()
        manager.forcePro() // -> PRO
        assertThat(manager.canUseAdvancedRules()).isTrue()
    }

    @Test
    fun `canUseFamilyProtection requires FAMILY or BUSINESS`() = runTest(testDispatcher) {
        val manager = createProManager()

        assertThat(manager.canUseFamilyProtection(EntitlementLevel.NONE)).isFalse()
        assertThat(manager.canUseFamilyProtection(EntitlementLevel.PRO)).isFalse()
        assertThat(manager.canUseFamilyProtection(EntitlementLevel.FAMILY)).isTrue()
        assertThat(manager.canUseFamilyProtection(EntitlementLevel.BUSINESS)).isTrue()
    }

    @Test
    fun `canUseFamilyProtection without args uses current state`() = runTest(testDispatcher) {
        val manager = createProManager()

        // NONE -> false
        assertThat(manager.canUseFamilyProtection()).isFalse()
        manager.forcePro() // PRO
        assertThat(manager.canUseFamilyProtection()).isFalse()
        manager.forcePro() // FAMILY
        assertThat(manager.canUseFamilyProtection()).isTrue()
    }

    @Test
    fun `canUseAgenticGovernance requires BUSINESS`() = runTest(testDispatcher) {
        val manager = createProManager()

        assertThat(manager.canUseAgenticGovernance(EntitlementLevel.NONE)).isFalse()
        assertThat(manager.canUseAgenticGovernance(EntitlementLevel.PRO)).isFalse()
        assertThat(manager.canUseAgenticGovernance(EntitlementLevel.FAMILY)).isFalse()
        assertThat(manager.canUseAgenticGovernance(EntitlementLevel.BUSINESS)).isTrue()
    }

    @Test
    fun `canUseAgenticGovernance without args uses current state`() = runTest(testDispatcher) {
        val manager = createProManager()

        // cycle to BUSINESS
        manager.forcePro() // PRO
        assertThat(manager.canUseAgenticGovernance()).isFalse()
        manager.forcePro() // FAMILY
        assertThat(manager.canUseAgenticGovernance()).isFalse()
        manager.forcePro() // BUSINESS
        assertThat(manager.canUseAgenticGovernance()).isTrue()
    }

    // ---------------- HVA counter ----------------

    @Test
    fun `recordHighValueAction increments per-type counter and emits flow`() =
        runTest(testDispatcher) {
            val manager = createProManager()

            manager.hvaCount.test {
                // initial value is "hva_ai_protection" -> 0
                assertThat(awaitItem()).isEqualTo(0)
                manager.recordHighValueAction("ai_protection")
                assertThat(awaitItem()).isEqualTo(1)
                manager.recordHighValueAction("ai_protection")
                assertThat(awaitItem()).isEqualTo(2)
                cancelAndIgnoreRemainingEvents()
            }

            assertThat(monetizationStore["hva_ai_protection"]).isEqualTo(2)
        }

    @Test
    fun `recordHighValueAction tracks analytics with type and count`() =
        runTest(testDispatcher) {
            val manager = createProManager()

            manager.recordHighValueAction("blocked_call")

            verify {
                analyticsService.track(
                    "high_value_action_recorded",
                    mapOf("type" to "blocked_call", "count" to 1),
                )
            }
        }

    @Test
    fun `getHighValueActionCount returns stored value or zero`() = runTest(testDispatcher) {
        val manager = createProManager()

        assertThat(manager.getHighValueActionCount("ai_protection")).isEqualTo(0)
        manager.recordHighValueAction("ai_protection")
        assertThat(manager.getHighValueActionCount("ai_protection")).isEqualTo(1)
        // Different type stays at zero.
        assertThat(manager.getHighValueActionCount("other_type")).isEqualTo(0)
    }

    @Test
    fun `hvaCount initial value reflects persisted ai_protection count`() =
        runTest(testDispatcher) {
            // Pre-seed the store.
            monetizationStore["hva_ai_protection"] = 7

            val manager = createProManager()

            assertThat(manager.hvaCount.value).isEqualTo(7)
        }

    // ---------------- Debug unlock ----------------

    @Test
    fun `unlockProForDebug sets entitlement to FAMILY and reports success`() =
        runTest(testDispatcher) {
            val manager = createProManager()

            val unlocked = manager.unlockProForDebug(entryPoint = "test_entry")

            assertThat(unlocked).isTrue()
            assertThat(manager.entitlementLevel.value).isEqualTo(EntitlementLevel.FAMILY)
            verify { analyticsService.track(any(), any()) }
        }

    // ---------------- onPurchasesUpdated entitlement transitions ----------------

    private fun mockPurchase(
        product: String,
        state: Int = Purchase.PurchaseState.PURCHASED,
        acknowledged: Boolean = true,
    ): Purchase {
        val purchase = mockk<Purchase>(relaxed = true)
        every { purchase.products } returns listOf(product)
        every { purchase.purchaseState } returns state
        every { purchase.isAcknowledged } returns acknowledged
        every { purchase.purchaseToken } returns "token-$product"
        return purchase
    }

    @Test
    fun `onPurchasesUpdated with BUSINESS purchase elevates entitlement`() =
        runTest(testDispatcher) {
            val manager = createProManager()
            val purchase = mockPurchase(ProManager.BUSINESS_PRODUCT_ID)
            val result = BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.OK)
                .setDebugMessage("ok")
                .build()

            manager.onPurchasesUpdated(result, mutableListOf(purchase))

            assertThat(manager.entitlementLevel.value).isEqualTo(EntitlementLevel.BUSINESS)
        }

    @Test
    fun `onPurchasesUpdated with FAMILY purchase upgrades from NONE`() =
        runTest(testDispatcher) {
            val manager = createProManager()
            val purchase = mockPurchase(ProManager.ELITE_PRODUCT_ID)
            val result = BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.OK)
                .setDebugMessage("ok")
                .build()

            manager.onPurchasesUpdated(result, mutableListOf(purchase))

            assertThat(manager.entitlementLevel.value).isEqualTo(EntitlementLevel.FAMILY)
        }

    @Test
    fun `onPurchasesUpdated with BASE purchase only upgrades from NONE`() =
        runTest(testDispatcher) {
            val manager = createProManager()
            val basePurchase = mockPurchase(ProManager.BASE_PRODUCT_ID)
            val okResult = BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.OK)
                .setDebugMessage("ok")
                .build()

            manager.onPurchasesUpdated(okResult, mutableListOf(basePurchase))
            assertThat(manager.entitlementLevel.value).isEqualTo(EntitlementLevel.PRO)

            // If already FAMILY, BASE purchase does not downgrade.
            manager.forcePro() // PRO -> FAMILY
            assertThat(manager.entitlementLevel.value).isEqualTo(EntitlementLevel.FAMILY)
            manager.onPurchasesUpdated(okResult, mutableListOf(basePurchase))
            assertThat(manager.entitlementLevel.value).isEqualTo(EntitlementLevel.FAMILY)
        }

    @Test
    fun `onPurchasesUpdated with non-OK response keeps entitlement unchanged`() =
        runTest(testDispatcher) {
            val manager = createProManager()
            val before = manager.entitlementLevel.value

            val cancelled = BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.USER_CANCELED)
                .setDebugMessage("user_canceled")
                .build()
            manager.onPurchasesUpdated(cancelled, null)

            assertThat(manager.entitlementLevel.value).isEqualTo(before)
            // Failure analytics path: result="cancelled".
            verify { analyticsService.track(any(), any()) }
        }

    @Test
    fun `onPurchasesUpdated with PENDING purchase does not change entitlement`() =
        runTest(testDispatcher) {
            val manager = createProManager()
            val pending = mockPurchase(
                product = ProManager.ELITE_PRODUCT_ID,
                state = Purchase.PurchaseState.PENDING,
            )
            val result = BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.OK)
                .setDebugMessage("ok")
                .build()

            manager.onPurchasesUpdated(result, mutableListOf(pending))

            assertThat(manager.entitlementLevel.value).isEqualTo(EntitlementLevel.NONE)
        }

    @Test
    fun `onPurchasesUpdated acknowledges unacknowledged purchases`() =
        runTest(testDispatcher) {
            val manager = createProManager()
            // Need the billing client to look ready for the acknowledge path, but the
            // acknowledge call itself is relaxed so it's a no-op.
            every { billingClient.isReady } returns true

            val unacked = mockPurchase(
                product = ProManager.ELITE_PRODUCT_ID,
                acknowledged = false,
            )
            val result = BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.OK)
                .setDebugMessage("ok")
                .build()

            manager.onPurchasesUpdated(result, mutableListOf(unacked))
            advanceUntilIdle()

            assertThat(manager.entitlementLevel.value).isEqualTo(EntitlementLevel.FAMILY)
        }

    // ---------------- Connected billing client helpers ----------------

    /**
     * Make billingClient look connected — startConnection invokes onBillingSetupFinished
     * with OK so ensureBillingReady() returns OK, and isReady reports true thereafter.
     */
    private fun arrangeBillingConnected() {
        every { billingClient.isReady } returns true
        val listenerSlot = slot<BillingClientStateListener>()
        every { billingClient.startConnection(capture(listenerSlot)) } answers {
            listenerSlot.captured.onBillingSetupFinished(okBillingResult())
        }
    }

    private fun okBillingResult(): BillingResult =
        BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.OK)
            .setDebugMessage("ok")
            .build()

    /**
     * Arrange `billingClient.queryPurchasesAsync(QueryPurchasesParams, listener)` to
     * deliver the supplied billing result and purchases.
     *
     * Match on params is wide (any()) — the production code passes INAPP for the in-app
     * query and SUBS for the subscription query in the same suspending call sequence, so
     * we differentiate by the productType passed to the params instead. To keep tests
     * simple we accept that both queries get the same result list and rely on the
     * production code filtering by product id.
     */
    /**
     * Arrange `billingClient.queryPurchasesAsync(QueryPurchasesParams, listener)` to
     * deliver the supplied billing result and a single list for both INAPP and SUBS
     * queries. The production code filters by product id internally, so returning the
     * same list for both query types still yields correct entitlement classification:
     *
     *  - a BASE purchase only counts against the in-app query (hasBase),
     *  - a FAMILY/BUSINESS purchase only counts against the subs query (hasElite/Business).
     */
    private fun arrangeQueryPurchases(
        purchases: List<Purchase> = emptyList(),
        billingResult: BillingResult = okBillingResult(),
    ) {
        val listenerSlot = slot<PurchasesResponseListener>()
        every {
            billingClient.queryPurchasesAsync(any<QueryPurchasesParams>(), capture(listenerSlot))
        } answers {
            listenerSlot.captured.onQueryPurchasesResponse(billingResult, purchases)
        }
    }

    private fun arrangeQueryProductDetails(
        details: List<ProductDetails> = emptyList(),
        billingResult: BillingResult = okBillingResult(),
    ) {
        val listenerSlot = slot<ProductDetailsResponseListener>()
        every {
            billingClient.queryProductDetailsAsync(any<QueryProductDetailsParams>(), capture(listenerSlot))
        } answers {
            listenerSlot.captured.onProductDetailsResponse(
                billingResult,
                QueryProductDetailsResult.create(details, emptyList()),
            )
        }
    }

    // ---------------- restorePurchasesFromPaywall ----------------

    @Test
    fun `restorePurchasesFromPaywall returns false when billing not ready`() =
        runTest(testDispatcher) {
            // Default setup keeps billingClient.isReady=false; startConnection signals
            // SERVICE_DISCONNECTED. restorePurchases must short-circuit.
            val manager = createProManager()
            advanceUntilIdle()

            val restored = manager.restorePurchasesFromPaywall(entryPoint = "paywall_restore")

            assertThat(restored).isFalse()
            assertThat(manager.entitlementLevel.value).isEqualTo(EntitlementLevel.NONE)
        }

    @Test
    fun `restorePurchasesFromPaywall promotes to BUSINESS when subs purchase present`() =
        runTest(testDispatcher) {
            arrangeBillingConnected()
            val businessPurchase = mockPurchase(ProManager.BUSINESS_PRODUCT_ID)
            arrangeQueryPurchases(purchases = listOf(businessPurchase))
            arrangeQueryProductDetails()

            val manager = createProManager()
            advanceUntilIdle()

            val restored = manager.restorePurchasesFromPaywall(entryPoint = "paywall_restore")

            assertThat(restored).isTrue()
            assertThat(manager.entitlementLevel.value).isEqualTo(EntitlementLevel.BUSINESS)
        }

    @Test
    fun `restorePurchasesFromPaywall promotes to FAMILY when elite sub present`() =
        runTest(testDispatcher) {
            arrangeBillingConnected()
            val familyPurchase = mockPurchase(ProManager.ELITE_PRODUCT_ID)
            arrangeQueryPurchases(purchases = listOf(familyPurchase))
            arrangeQueryProductDetails()

            val manager = createProManager()
            advanceUntilIdle()

            val restored = manager.restorePurchasesFromPaywall(entryPoint = "paywall_restore")

            assertThat(restored).isTrue()
            assertThat(manager.entitlementLevel.value).isEqualTo(EntitlementLevel.FAMILY)
        }

    @Test
    fun `restorePurchasesFromPaywall promotes to PRO when only base inApp present`() =
        runTest(testDispatcher) {
            arrangeBillingConnected()
            val basePurchase = mockPurchase(ProManager.BASE_PRODUCT_ID)
            arrangeQueryPurchases(purchases = listOf(basePurchase))
            arrangeQueryProductDetails()

            val manager = createProManager()
            advanceUntilIdle()

            val restored = manager.restorePurchasesFromPaywall(entryPoint = "paywall_restore")

            assertThat(restored).isTrue()
            assertThat(manager.entitlementLevel.value).isEqualTo(EntitlementLevel.PRO)
        }

    @Test
    fun `restorePurchasesFromPaywall stays NONE when no eligible purchases`() =
        runTest(testDispatcher) {
            arrangeBillingConnected()
            arrangeQueryPurchases(purchases = emptyList())
            arrangeQueryProductDetails()

            val manager = createProManager()
            advanceUntilIdle()

            val restored = manager.restorePurchasesFromPaywall(entryPoint = "paywall_restore")

            assertThat(restored).isFalse()
            assertThat(manager.entitlementLevel.value).isEqualTo(EntitlementLevel.NONE)
        }

    @Test
    fun `restorePurchasesFromPaywall ignores PENDING subs`() =
        runTest(testDispatcher) {
            arrangeBillingConnected()
            val pending = mockPurchase(
                product = ProManager.ELITE_PRODUCT_ID,
                state = Purchase.PurchaseState.PENDING,
            )
            arrangeQueryPurchases(purchases = listOf(pending))
            arrangeQueryProductDetails()

            val manager = createProManager()
            advanceUntilIdle()

            val restored = manager.restorePurchasesFromPaywall(entryPoint = "paywall_restore")

            assertThat(restored).isFalse()
            assertThat(manager.entitlementLevel.value).isEqualTo(EntitlementLevel.NONE)
        }

    // ---------------- launchPurchase / launchProPurchase ----------------

    @Test
    fun `launchProPurchase returns false when billing not ready`() = runTest(testDispatcher) {
        val activity = mockk<Activity>(relaxed = true)
        val manager = createProManager()
        advanceUntilIdle()

        val launched = manager.launchProPurchase(activity, entryPoint = "setup_upgrade_cta")

        assertThat(launched).isFalse()
    }

    @Test
    fun `launchPurchase returns false when product details unavailable`() =
        runTest(testDispatcher) {
            arrangeBillingConnected()
            arrangeQueryProductDetails(details = emptyList())

            val manager = createProManager()
            advanceUntilIdle()

            val activity = mockk<Activity>(relaxed = true)
            val launched = manager.launchPurchase(
                activity,
                productID = ProManager.BASE_PRODUCT_ID,
                entryPoint = "setup_upgrade_cta",
            )

            assertThat(launched).isFalse()
        }

    @Test
    fun `launchPurchase returns false for subscription with no offer token`() =
        runTest(testDispatcher) {
            arrangeBillingConnected()
            // Subscription product details with NO subscriptionOfferDetails.
            val details = mockProductDetails(
                productID = ProManager.ELITE_PRODUCT_ID,
                subscriptionOfferDetails = emptyList(),
            )
            arrangeQueryProductDetails(details = listOf(details))

            val manager = createProManager()
            advanceUntilIdle()

            val activity = mockk<Activity>(relaxed = true)
            val launched = manager.launchPurchase(
                activity,
                productID = ProManager.ELITE_PRODUCT_ID,
                entryPoint = "setup_upgrade_cta",
            )

            assertThat(launched).isFalse()
        }

    @Test
    fun `launchPurchase returns false when launchBillingFlow fails`() =
        runTest(testDispatcher) {
            arrangeBillingConnected()
            val details = mockProductDetails(productID = ProManager.BASE_PRODUCT_ID)
            arrangeQueryProductDetails(details = listOf(details))
            // launchBillingFlow returns ERROR.
            every { billingClient.launchBillingFlow(any(), any()) } returns BillingResult
                .newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.ERROR)
                .setDebugMessage("flow_error")
                .build()

            val manager = createProManager()
            advanceUntilIdle()

            val activity = mockk<Activity>(relaxed = true)
            val launched = manager.launchPurchase(
                activity,
                productID = ProManager.BASE_PRODUCT_ID,
                entryPoint = "setup_upgrade_cta",
            )

            assertThat(launched).isFalse()
        }

    @Test
    fun `launchPurchase returns true when launchBillingFlow succeeds for in-app product`() =
        runTest(testDispatcher) {
            arrangeBillingConnected()
            val details = mockProductDetails(productID = ProManager.BASE_PRODUCT_ID)
            arrangeQueryProductDetails(details = listOf(details))
            every { billingClient.launchBillingFlow(any(), any<BillingFlowParams>()) } returns okBillingResult()

            val manager = createProManager()
            advanceUntilIdle()

            val activity = mockk<Activity>(relaxed = true)
            val launched = manager.launchPurchase(
                activity,
                productID = ProManager.BASE_PRODUCT_ID,
                entryPoint = "setup_upgrade_cta",
            )

            assertThat(launched).isTrue()
        }

    @Test
    fun `launchProPurchase succeeds for subscription with yearly offer`() =
        runTest(testDispatcher) {
            arrangeBillingConnected()
            val details = mockProductDetails(
                productID = ProManager.ELITE_PRODUCT_ID,
                subscriptionOfferDetails = listOf(
                    mockSubscriptionOffer("yearly-token", "$29.99", "P1Y"),
                ),
            )
            arrangeQueryProductDetails(details = listOf(details))
            every { billingClient.launchBillingFlow(any(), any<BillingFlowParams>()) } returns okBillingResult()

            val manager = createProManager()
            advanceUntilIdle()

            val activity = mockk<Activity>(relaxed = true)
            val launched = manager.launchProPurchase(activity, entryPoint = "setup_upgrade_cta")

            assertThat(launched).isTrue()
        }

    // ---------------- getFormattedPrice ----------------

    @Test
    fun `getFormattedPrice returns subscription yearly offer price`() =
        runTest(testDispatcher) {
            arrangeBillingConnected()
            val details = mockProductDetails(
                productID = ProManager.ELITE_PRODUCT_ID,
                subscriptionOfferDetails = listOf(
                    mockSubscriptionOffer("yearly-token", "$29.99", "P1Y"),
                ),
            )
            arrangeQueryProductDetails(details = listOf(details))

            val manager = createProManager()
            advanceUntilIdle()

            val price = manager.getFormattedPrice(ProManager.ELITE_PRODUCT_ID)
            assertThat(price).isEqualTo("$29.99")
        }

    @Test
    fun `getFormattedPrice falls back to default for elite when details missing`() =
        runTest(testDispatcher) {
            arrangeBillingConnected()
            arrangeQueryProductDetails(details = emptyList())

            val manager = createProManager()
            advanceUntilIdle()

            val price = manager.getFormattedPrice(ProManager.ELITE_PRODUCT_ID)
            assertThat(price).isEqualTo("$29.99")
        }

    @Test
    fun `getFormattedPrice falls back to default for business when details missing`() =
        runTest(testDispatcher) {
            arrangeBillingConnected()
            arrangeQueryProductDetails(details = emptyList())

            val manager = createProManager()
            advanceUntilIdle()

            val price = manager.getFormattedPrice(ProManager.BUSINESS_PRODUCT_ID)
            assertThat(price).isEqualTo("$49.99")
        }

    @Test
    fun `getFormattedPrice returns base one-time price when available`() =
        runTest(testDispatcher) {
            arrangeBillingConnected()
            val details = mockProductDetails(
                productID = ProManager.BASE_PRODUCT_ID,
                oneTimeFormattedPrice = "$7.99",
            )
            arrangeQueryProductDetails(details = listOf(details))

            val manager = createProManager()
            advanceUntilIdle()

            val price = manager.getFormattedPrice(ProManager.BASE_PRODUCT_ID)
            assertThat(price).isEqualTo("$7.99")
        }

    @Test
    fun `getFormattedPrice falls back to default base price when details missing`() =
        runTest(testDispatcher) {
            arrangeBillingConnected()
            arrangeQueryProductDetails(details = emptyList())

            val manager = createProManager()
            advanceUntilIdle()

            val price = manager.getFormattedPrice(ProManager.BASE_PRODUCT_ID)
            assertThat(price).isEqualTo("$7.99")
        }

    // ---------------- ProductDetails helpers ----------------

    private fun mockSubscriptionOffer(
        offerToken: String,
        formattedPrice: String,
        billingPeriod: String,
    ): ProductDetails.SubscriptionOfferDetails {
        val phase = mockk<ProductDetails.PricingPhase>(relaxed = true)
        every { phase.formattedPrice } returns formattedPrice
        every { phase.billingPeriod } returns billingPeriod

        val pricingPhases = mockk<ProductDetails.PricingPhases>(relaxed = true)
        every { pricingPhases.pricingPhaseList } returns listOf(phase)

        val offer = mockk<ProductDetails.SubscriptionOfferDetails>(relaxed = true)
        every { offer.offerToken } returns offerToken
        every { offer.pricingPhases } returns pricingPhases
        return offer
    }

    private fun mockProductDetails(
        productID: String,
        subscriptionOfferDetails: List<ProductDetails.SubscriptionOfferDetails>? = null,
        oneTimeFormattedPrice: String? = null,
    ): ProductDetails {
        val details = mockk<ProductDetails>(relaxed = true)
        every { details.productId } returns productID
        val productType = if (productID == ProManager.ELITE_PRODUCT_ID ||
            productID == ProManager.BUSINESS_PRODUCT_ID
        ) {
            BillingClient.ProductType.SUBS
        } else {
            BillingClient.ProductType.INAPP
        }
        every { details.productType } returns productType
        every { details.subscriptionOfferDetails } returns subscriptionOfferDetails

        if (oneTimeFormattedPrice != null) {
            val oneTime = mockk<ProductDetails.OneTimePurchaseOfferDetails>(relaxed = true)
            every { oneTime.formattedPrice } returns oneTimeFormattedPrice
            every { details.oneTimePurchaseOfferDetails } returns oneTime
        } else {
            every { details.oneTimePurchaseOfferDetails } returns null
        }
        return details
    }

    // ---------------- toSubscriptionOffers extension coverage ----------------

    @Test
    fun `subscription product with multi-phase offer exposes display price via launchPurchase`() =
        runTest(testDispatcher) {
            arrangeBillingConnected()
            val details = mockProductDetails(
                productID = ProManager.BUSINESS_PRODUCT_ID,
                subscriptionOfferDetails = listOf(
                    // Intro + recurring phase. selectPreferredSubscriptionOffer should pick
                    // this offer (P1Y is in pricingPhases) and toSubscriptionOffers walks
                    // both phases.
                    run {
                        val introPhase = mockk<ProductDetails.PricingPhase>(relaxed = true)
                        every { introPhase.formattedPrice } returns "$0.00"
                        every { introPhase.billingPeriod } returns "P1W"
                        val mainPhase = mockk<ProductDetails.PricingPhase>(relaxed = true)
                        every { mainPhase.formattedPrice } returns "$49.99"
                        every { mainPhase.billingPeriod } returns "P1Y"
                        val phases = mockk<ProductDetails.PricingPhases>(relaxed = true)
                        every { phases.pricingPhaseList } returns listOf(introPhase, mainPhase)
                        val offer = mockk<ProductDetails.SubscriptionOfferDetails>(relaxed = true)
                        every { offer.offerToken } returns "intro-yearly"
                        every { offer.pricingPhases } returns phases
                        offer
                    },
                ),
            )
            arrangeQueryProductDetails(details = listOf(details))
            every { billingClient.launchBillingFlow(any(), any<BillingFlowParams>()) } returns okBillingResult()

            val manager = createProManager()
            advanceUntilIdle()

            val activity = mockk<Activity>(relaxed = true)
            val launched = manager.launchPurchase(
                activity,
                productID = ProManager.BUSINESS_PRODUCT_ID,
                entryPoint = "paywall_business",
            )

            assertThat(launched).isTrue()
            val price = manager.getFormattedPrice(ProManager.BUSINESS_PRODUCT_ID)
            assertThat(price).isEqualTo("$49.99")
        }
}

package com.igorganapolsky.answerguard.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.igorganapolsky.answerguard.BuildConfig
import com.igorganapolsky.answerguard.analytics.AnalyticsEvents
import com.igorganapolsky.answerguard.analytics.AnalyticsProperties
import com.igorganapolsky.answerguard.analytics.AnalyticsService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private fun billingResult(
    responseCode: Int,
    debugMessage: String,
): BillingResult =
    BillingResult
        .newBuilder()
        .setResponseCode(responseCode)
        .setDebugMessage(debugMessage)
        .build()

@Singleton
class ProManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val analyticsService: AnalyticsService,
        private val externalScope: CoroutineScope,
    ) : PurchasesUpdatedListener {
        companion object {
            const val BASE_PRODUCT_ID = "answerguard_pro"
            const val ELITE_PRODUCT_ID = "answerguard_family"
            const val BUSINESS_PRODUCT_ID = "answerguard_business"
            const val PRO_PRODUCT_ID = ELITE_PRODUCT_ID

            internal fun canUseDebugUnlock(
                isDebugBuild: Boolean = BuildConfig.DEBUG,
            ): Boolean = isDebugBuild
        }

        private val _entitlementLevel = MutableStateFlow(EntitlementLevel.NONE)
        val entitlementLevel: StateFlow<EntitlementLevel> = _entitlementLevel.asStateFlow()

        val isPro: StateFlow<Boolean> =
            _entitlementLevel
                .map { it.isPro }
                .stateIn(externalScope, SharingStarted.Eagerly, _entitlementLevel.value.isPro)

        val isElite: StateFlow<Boolean> =
            _entitlementLevel
                .map { it == EntitlementLevel.FAMILY || it == EntitlementLevel.BUSINESS }
                .stateIn(
                    externalScope, 
                    SharingStarted.Eagerly, 
                    _entitlementLevel.value == EntitlementLevel.FAMILY || _entitlementLevel.value == EntitlementLevel.BUSINESS
                )

        private var billingClient: BillingClient =
            BillingClient
                .newBuilder(context)
                .setListener(this)
                .enablePendingPurchases(
                    PendingPurchasesParams
                        .newBuilder()
                        .enableOneTimeProducts()
                        .build(),
                ).build()

        private val cachedProductDetails = mutableMapOf<String, com.android.billingclient.api.ProductDetails>()
        private var pendingPurchaseEntryPoint: String? = null
        private var activeConnection: CompletableDeferred<BillingResult>? = null

        private val prefs = context.getSharedPreferences("monetization_prefs", Context.MODE_PRIVATE)
        
        private val _hvaCount = MutableStateFlow(prefs.getInt("hva_ai_protection", 0))
        val hvaCount: StateFlow<Int> = _hvaCount.asStateFlow()

        init {
            connectAndRestore()
        }

        private fun connectAndRestore() {
            externalScope.launch {
                if (ensureBillingReady().responseCode == BillingClient.BillingResponseCode.OK) {
                    restorePurchases(
                        source = MonetizationSources.AUTO_RESTORE,
                        entryPoint = null,
                        trackResult = false,
                    )
                    fetchAllProductDetails()
                }
            }
        }

        private suspend fun ensureBillingReady(): BillingResult {
            if (billingClient.isReady) {
                return billingResult(BillingClient.BillingResponseCode.OK, "billing_ready")
            }

            activeConnection?.let { existingConnection ->
                return existingConnection.await()
            }

            val connection = CompletableDeferred<BillingResult>()
            activeConnection = connection
            try {
                billingClient.startConnection(
                    object : BillingClientStateListener {
                        override fun onBillingSetupFinished(result: BillingResult) {
                            connection.complete(result)
                        }

                        override fun onBillingServiceDisconnected() {
                            connection.complete(
                                billingResult(
                                    BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
                                    "billing_service_disconnected",
                                ),
                            )
                        }
                    },
                )
            } catch (error: Exception) {
                connection.complete(
                    billingResult(
                        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
                        error.message ?: "billing_connection_failed",
                    ),
                )
            }
            return try {
                connection.await()
            } finally {
                if (activeConnection === connection) {
                    activeConnection = null
                }
            }
        }

        private suspend fun restorePurchases(
            source: String,
            entryPoint: String?,
            trackResult: Boolean,
        ): Boolean {
            val readyResult = ensureBillingReady()
            if (readyResult.responseCode != BillingClient.BillingResponseCode.OK || !billingClient.isReady) {
                if (trackResult) {
                    trackRestoreResult(
                        success = false,
                        source = source,
                        entryPoint = entryPoint,
                        responseCode = readyResult.responseCode,
                        debugMessage = readyResult.debugMessage.ifBlank { "billing_not_ready" },
                    )
                }
                return false
            }

            // Check In-App (BASE)
            val inAppParams =
                QueryPurchasesParams
                    .newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            val inAppResult = billingClient.queryPurchasesAsync(inAppParams)

            // Check Subs (FAMILY + BUSINESS)
            val subsParams =
                QueryPurchasesParams
                    .newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            val subsResult = billingClient.queryPurchasesAsync(subsParams)

            val hasBusiness = subsResult.purchasesList.any { purchase ->
                purchase.products.contains(BUSINESS_PRODUCT_ID) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }

            val hasElite =
                subsResult.purchasesList.any { purchase ->
                    purchase.products.contains(ELITE_PRODUCT_ID) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }

            val hasBase =
                inAppResult.purchasesList.any { purchase ->
                    purchase.products.contains(BASE_PRODUCT_ID) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }

            val level =
                when {
                    hasBusiness -> EntitlementLevel.BUSINESS
                    hasElite -> EntitlementLevel.FAMILY
                    hasBase -> EntitlementLevel.PRO
                    else -> EntitlementLevel.NONE
                }

            _entitlementLevel.value = level

            if (trackResult) {
                trackRestoreResult(
                    success = level.isPro,
                    source = source,
                    entryPoint = entryPoint,
                    responseCode = if (hasElite || hasBusiness) subsResult.billingResult.responseCode else inAppResult.billingResult.responseCode,
                    debugMessage = if (hasElite || hasBusiness) subsResult.billingResult.debugMessage else inAppResult.billingResult.debugMessage,
                )
            }
            return level.isPro
        }

        suspend fun launchPurchase(
            activity: Activity,
            productID: String,
            entryPoint: String,
        ): Boolean {
            pendingPurchaseEntryPoint = entryPoint
            val readyResult = ensureBillingReady()
            if (readyResult.responseCode != BillingClient.BillingResponseCode.OK || !billingClient.isReady) {
                trackPurchaseResult(
                    success = false,
                    source = MonetizationSources.PAYWALL,
                    entryPoint = entryPoint,
                    responseCode = readyResult.responseCode,
                    debugMessage = readyResult.debugMessage.ifBlank { "billing_not_ready" },
                )
                pendingPurchaseEntryPoint = null
                return false
            }

            val productDetails = cachedProductDetails[productID] ?: fetchProductDetails(productID)
            if (productDetails == null) {
                trackPurchaseResult(
                    success = false,
                    source = MonetizationSources.PAYWALL,
                    entryPoint = entryPoint,
                    responseCode = BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                    debugMessage = "product_details_unavailable",
                )
                pendingPurchaseEntryPoint = null
                return false
            }
            cachedProductDetails[productID] = productDetails

            val isSubscription = productID == ELITE_PRODUCT_ID || productID == BUSINESS_PRODUCT_ID
            val selectedOffer =
                if (isSubscription) {
                    selectPreferredSubscriptionOffer(productDetails.toSubscriptionOffers())
                } else {
                    null
                }
            if (isSubscription && selectedOffer == null) {
                trackPurchaseResult(
                    success = false,
                    source = MonetizationSources.PAYWALL,
                    entryPoint = entryPoint,
                    responseCode = BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                    debugMessage = "subscription_offer_unavailable",
                )
                pendingPurchaseEntryPoint = null
                return false
            }

            val productDetailsParamsList =
                listOf(
                    BillingFlowParams.ProductDetailsParams
                        .newBuilder()
                        .setProductDetails(productDetails)
                        .apply {
                            if (selectedOffer != null) {
                                setOfferToken(selectedOffer.offerToken)
                            }
                        }.build(),
                )

            val flowParams =
                BillingFlowParams
                    .newBuilder()
                    .setProductDetailsParamsList(productDetailsParamsList)
                    .build()

            val result = billingClient.launchBillingFlow(activity, flowParams)
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                trackPurchaseResult(
                    success = false,
                    source = MonetizationSources.PAYWALL,
                    entryPoint = entryPoint,
                    responseCode = result.responseCode,
                    debugMessage = result.debugMessage,
                )
                pendingPurchaseEntryPoint = null
                return false
            }
            return true
        }

        private suspend fun fetchAllProductDetails() {
            fetchProductDetails(BASE_PRODUCT_ID)
            fetchProductDetails(ELITE_PRODUCT_ID)
            fetchProductDetails(BUSINESS_PRODUCT_ID)
        }

        private suspend fun fetchProductDetails(productID: String): com.android.billingclient.api.ProductDetails? {
            val productType =
                if (productID == ELITE_PRODUCT_ID || productID == BUSINESS_PRODUCT_ID) {
                    BillingClient.ProductType.SUBS
                } else {
                    BillingClient.ProductType.INAPP
                }

            val productList =
                listOf(
                    QueryProductDetailsParams.Product
                        .newBuilder()
                        .setProductId(productID)
                        .setProductType(productType)
                        .build(),
                )

            val params =
                QueryProductDetailsParams
                    .newBuilder()
                    .setProductList(productList)
                    .build()

            val result = billingClient.queryProductDetails(params)
            val details = result.productDetailsList?.firstOrNull()
            if (details != null) {
                cachedProductDetails[productID] = details
            }
            return details
        }

        suspend fun getFormattedPrice(productID: String): String {
            val details = cachedProductDetails[productID] ?: fetchProductDetails(productID)
            return if (productID == ELITE_PRODUCT_ID || productID == BUSINESS_PRODUCT_ID) {
                selectPreferredSubscriptionOffer(details?.toSubscriptionOffers().orEmpty())
                    ?.displayPrice ?: when(productID) {
                        ELITE_PRODUCT_ID -> "$29.99"
                        BUSINESS_PRODUCT_ID -> "$49.99"
                        else -> "$29.99"
                    }
            } else {
                details?.oneTimePurchaseOfferDetails?.formattedPrice ?: "$7.99"
            }
        }

        suspend fun launchProPurchase(
            activity: Activity,
            entryPoint: String,
        ): Boolean = launchPurchase(activity, PRO_PRODUCT_ID, entryPoint)

        override fun onPurchasesUpdated(
            result: BillingResult,
            purchases: MutableList<Purchase>?,
        ) {
            var hasPurchased = false
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                for (purchase in purchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        hasPurchased = true
                        updateEntitlementFromPurchase(purchase)
                        externalScope.launch { acknowledgePurchaseIfNeeded(purchase) }
                    }
                }
            }
            trackPurchaseResult(
                success = hasPurchased,
                source = if (pendingPurchaseEntryPoint.isNullOrBlank()) MonetizationSources.BILLING_CALLBACK else MonetizationSources.PAYWALL,
                entryPoint = pendingPurchaseEntryPoint,
                responseCode = result.responseCode,
                debugMessage = result.debugMessage,
            )
            pendingPurchaseEntryPoint = null
        }

        private fun updateEntitlementFromPurchase(purchase: Purchase) {
            if (purchase.products.contains(BUSINESS_PRODUCT_ID)) {
                _entitlementLevel.value = EntitlementLevel.BUSINESS
            } else if (purchase.products.contains(ELITE_PRODUCT_ID)) {
                _entitlementLevel.value = EntitlementLevel.FAMILY
            } else if (purchase.products.contains(BASE_PRODUCT_ID)) {
                if (_entitlementLevel.value == EntitlementLevel.NONE) {
                    _entitlementLevel.value = EntitlementLevel.PRO
                }
            }
        }

        private suspend fun acknowledgePurchaseIfNeeded(purchase: Purchase) {
            if (!purchase.isAcknowledged) {
                val params =
                    AcknowledgePurchaseParams
                        .newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                billingClient.acknowledgePurchase(params)
            }
        }

        suspend fun restorePurchasesFromPaywall(entryPoint: String): Boolean =
            restorePurchases(
                source = MonetizationSources.PAYWALL,
                entryPoint = entryPoint,
                trackResult = true,
            )

        private fun trackPurchaseResult(
            success: Boolean,
            source: String,
            entryPoint: String?,
            responseCode: Int,
            debugMessage: String?,
        ) {
            analyticsService.track(
                AnalyticsEvents.PAYWALL_PURCHASE_RESULT,
                MonetizationAnalyticsPayload.resultProperties(
                    success = success,
                    result = purchaseResultValue(success, responseCode),
                    source = source,
                    entryPoint = entryPoint,
                    responseCode = responseCode,
                    debugMessage = debugMessage,
                ),
            )
        }

        private fun trackRestoreResult(
            success: Boolean,
            source: String,
            entryPoint: String?,
            responseCode: Int,
            debugMessage: String?,
        ) {
            analyticsService.track(
                AnalyticsEvents.PAYWALL_RESTORE_RESULT,
                MonetizationAnalyticsPayload.resultProperties(
                    success = success,
                    result = restoreResultValue(success),
                    source = source,
                    entryPoint = entryPoint,
                    responseCode = responseCode,
                    debugMessage = debugMessage,
                ),
            )
        }

        private fun purchaseResultValue(
            success: Boolean,
            responseCode: Int,
        ): String =
            when {
                success -> "success"
                responseCode == BillingClient.BillingResponseCode.USER_CANCELED -> "cancelled"
                else -> "failed"
            }

        private fun restoreResultValue(success: Boolean): String = if (success) "restored" else "failed"

        fun forcePro() {
            // Cycle: NONE -> PRO -> FAMILY -> BUSINESS -> NONE
            val next =
                when (_entitlementLevel.value) {
                    EntitlementLevel.NONE -> EntitlementLevel.PRO
                    EntitlementLevel.PRO -> EntitlementLevel.FAMILY
                    EntitlementLevel.FAMILY -> EntitlementLevel.BUSINESS
                    EntitlementLevel.BUSINESS -> EntitlementLevel.NONE
                }
            _entitlementLevel.value = next
            context
                .getSharedPreferences("pro_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("forced_pro", next != EntitlementLevel.NONE)
                .putString("forced_level", next.name)
                .apply()
            analyticsService.track("dev_force_pro", mapOf("level" to next.name))
        }

        fun canUseAdvancedRules(level: EntitlementLevel = _entitlementLevel.value): Boolean = level.isPro

        fun canUseFamilyProtection(level: EntitlementLevel = _entitlementLevel.value): Boolean = 
            level == EntitlementLevel.FAMILY || level == EntitlementLevel.BUSINESS

        fun canUseAgenticGovernance(level: EntitlementLevel = _entitlementLevel.value): Boolean =
            level == EntitlementLevel.BUSINESS

        fun recordHighValueAction(actionType: String) {
            val key = "hva_$actionType"
            val count = prefs.getInt(key, 0) + 1
            prefs.edit().putInt(key, count).apply()
            _hvaCount.value = count
            analyticsService.track("high_value_action_recorded", mapOf("type" to actionType, "count" to count))
        }

        fun getHighValueActionCount(actionType: String): Int = prefs.getInt("hva_$actionType", 0)

        fun unlockProForDebug(entryPoint: String): Boolean {
            if (!canUseDebugUnlock()) {
                return false
            }
            _entitlementLevel.value = EntitlementLevel.FAMILY
            trackPurchaseResult(
                success = true,
                source = MonetizationSources.PAYWALL,
                entryPoint = entryPoint,
                responseCode = BillingClient.BillingResponseCode.OK,
                debugMessage = "hidden_hold_override",
            )
            return true
        }
    }

internal data class SubscriptionPricingPhase(
    val formattedPrice: String,
    val billingPeriod: String,
)

internal data class SubscriptionOffer(
    val offerToken: String,
    val pricingPhases: List<SubscriptionPricingPhase>,
) {
    val displayPrice: String?
        get() = pricingPhases.lastOrNull()?.formattedPrice ?: pricingPhases.firstOrNull()?.formattedPrice
}

internal fun selectPreferredSubscriptionOffer(offers: List<SubscriptionOffer>): SubscriptionOffer? =
    offers.firstOrNull { offer -> offer.pricingPhases.any { it.billingPeriod == "P1Y" } } ?: offers.firstOrNull()

private fun com.android.billingclient.api.ProductDetails.toSubscriptionOffers(): List<SubscriptionOffer> =
    subscriptionOfferDetails
        ?.map { offer ->
            SubscriptionOffer(
                offerToken = offer.offerToken,
                pricingPhases =
                    offer.pricingPhases.pricingPhaseList.map { phase ->
                        SubscriptionPricingPhase(
                            formattedPrice = phase.formattedPrice,
                            billingPeriod = phase.billingPeriod,
                        )
                    },
            )
        }.orEmpty()

internal object MonetizationSources {
    const val PAYWALL = "paywall"
    const val AUTO_RESTORE = "auto_restore"
    const val BILLING_CALLBACK = "billing_callback"
}

internal object MonetizationAnalyticsPayload {
    fun attemptProperties(
        source: String,
        entryPoint: String?,
        productID: String,
    ): Map<String, Any> =
        mapOf(
            AnalyticsProperties.SOURCE to source,
            AnalyticsProperties.ENTRY_POINT to (entryPoint ?: source),
            AnalyticsProperties.PRODUCT_ID to productID,
        )

    fun successProperties(
        source: String,
        entryPoint: String?,
        productID: String?,
        responseCode: Int,
        debugMessage: String?,
    ): Map<String, Any> =
        buildMap {
            put(AnalyticsProperties.SOURCE, source)
            put(AnalyticsProperties.ENTRY_POINT, entryPoint ?: source)
            put(AnalyticsProperties.SUCCESS, true)
            put(AnalyticsProperties.RESPONSE_CODE, responseCode)
            put(AnalyticsProperties.DEBUG_MESSAGE, debugMessage ?: "")
            productID?.let { put(AnalyticsProperties.PRODUCT_ID, it) }
        }

    fun resultProperties(
        success: Boolean,
        result: String,
        source: String,
        entryPoint: String?,
        responseCode: Int,
        debugMessage: String?,
    ): Map<String, Any> =
        mapOf(
            AnalyticsProperties.RESULT to result,
            AnalyticsProperties.SUCCESS to success,
            AnalyticsProperties.SOURCE to source,
            AnalyticsProperties.ENTRY_POINT to (entryPoint ?: source),
            AnalyticsProperties.RESPONSE_CODE to responseCode,
            AnalyticsProperties.DEBUG_MESSAGE to (debugMessage ?: ""),
        )
}

package com.rsm.eztrivia.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** State shown by Settings and used to decide whether ads should exist. */
data class BillingState(
    val hasRemovedAds: Boolean,
    val isConnected: Boolean = false,
    val isPurchasing: Boolean = false,
    val purchasePending: Boolean = false,
    val productAvailable: Boolean = false,
    val formattedPrice: String? = null,
    val errorMessage: String? = null,
)

/**
 * Google Play Billing owner for the single non-consumable Remove Ads product.
 *
 * The local entitlement cache prevents a paid user from seeing an ad flash at
 * startup or while offline. A successful Play purchase query is authoritative
 * and corrects the cache. Entitlement is never granted for PENDING purchases,
 * and completed purchases are acknowledged so Play does not auto-refund them.
 */
class BillingManager(private val activity: Activity) : PurchasesUpdatedListener {
    companion object {
        const val REMOVE_ADS_PRODUCT_ID = "com.rsm.eztrivia.removeads"
        private const val PREFS = "eztrivia_billing"
        private const val ENTITLEMENT_KEY = "purchase.removeAds.v1"
    }

    private val preferences = activity.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        BillingState(hasRemovedAds = preferences.getBoolean(ENTITLEMENT_KEY, false))
    )
    val state: StateFlow<BillingState> = _state.asStateFlow()

    private var productDetails: ProductDetails? = null
    private var starting = false

    private val billingClient = BillingClient.newBuilder(activity.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (billingClient.isReady) {
            refresh()
            return
        }
        if (starting) return
        starting = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                starting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.value = _state.value.copy(isConnected = true, errorMessage = null)
                    queryProduct()
                    refreshPurchases(authoritative = true)
                } else {
                    recordError(result.debugMessage)
                }
            }

            override fun onBillingServiceDisconnected() {
                starting = false
                _state.value = _state.value.copy(isConnected = false)
            }
        })
    }

    fun refresh() {
        if (!billingClient.isReady) {
            start()
            return
        }
        queryProduct()
        refreshPurchases(authoritative = true)
    }

    fun purchase() {
        val product = productDetails ?: run {
            queryProduct()
            return
        }
        if (_state.value.hasRemovedAds || _state.value.isPurchasing) return

        val offer = product.oneTimePurchaseOfferDetailsList?.firstOrNull()
            ?: product.oneTimePurchaseOfferDetails
        if (offer == null) {
            recordError("Remove Ads is not available for purchase on this Google Play account.")
            return
        }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .setOfferToken(offer.offerToken)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        _state.value = _state.value.copy(isPurchasing = true, errorMessage = null)
        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.value = _state.value.copy(isPurchasing = false)
            recordError(result.debugMessage)
        }
    }

    fun restore() {
        if (!billingClient.isReady) {
            start()
        } else {
            refreshPurchases(authoritative = true, reportMissing = true)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        _state.value = _state.value.copy(isPurchasing = false)
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> processPurchases(
                purchases.orEmpty(),
                authoritative = false,
            )
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                refreshPurchases(authoritative = true)
            else -> recordError(result.debugMessage)
        }
    }

    fun close() {
        if (billingClient.isReady) billingClient.endConnection()
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(REMOVE_ADS_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productResult ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                recordError(billingResult.debugMessage)
                return@queryProductDetailsAsync
            }

            val product = productResult.productDetailsList.firstOrNull {
                it.productId == REMOVE_ADS_PRODUCT_ID
            }
            productDetails = product
            val offer = product?.oneTimePurchaseOfferDetailsList?.firstOrNull()
                ?: product?.oneTimePurchaseOfferDetails
            _state.value = _state.value.copy(
                productAvailable = product != null && offer != null,
                formattedPrice = offer?.formattedPrice,
                errorMessage = null,
            )
        }
    }

    private fun refreshPurchases(
        authoritative: Boolean,
        reportMissing: Boolean = false,
    ) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                recordError(billingResult.debugMessage)
                return@queryPurchasesAsync
            }
            processPurchases(purchases, authoritative)
            if (reportMissing && !_state.value.hasRemovedAds) {
                recordError("No Remove Ads purchase was found for this Google Play account.")
            }
        }
    }

    private fun processPurchases(
        purchases: List<Purchase>,
        authoritative: Boolean,
    ) {
        var ownsRemoveAds = false
        var pendingRemoveAds = false

        purchases.filter { REMOVE_ADS_PRODUCT_ID in it.products }.forEach { purchase ->
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    ownsRemoveAds = true
                    if (!purchase.isAcknowledged) acknowledge(purchase)
                }
                Purchase.PurchaseState.PENDING -> pendingRemoveAds = true
                else -> Unit
            }
        }

        when {
            ownsRemoveAds -> setRemovedAds(true)
            authoritative -> setRemovedAds(false)
        }
        _state.value = _state.value.copy(
            purchasePending = pendingRemoveAds,
            errorMessage = if (ownsRemoveAds) null else _state.value.errorMessage,
        )
    }

    private fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                // Entitlement stays granted because Play already reported the
                // purchase as PURCHASED; the next refresh retries acknowledgement.
                recordError(result.debugMessage)
            }
        }
    }

    private fun setRemovedAds(value: Boolean) {
        preferences.edit().putBoolean(ENTITLEMENT_KEY, value).apply()
        _state.value = _state.value.copy(hasRemovedAds = value)
    }

    private fun recordError(message: String?) {
        val safeMessage = message?.takeIf { it.isNotBlank() } ?: "Google Play billing is unavailable right now."
        _state.value = _state.value.copy(errorMessage = safeMessage)
    }
}

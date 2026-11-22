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

/**
 * Owns the one-time Google Play "Remove Ads" entitlement.
 *
 * The entitlement is cached so a paying player never sees an ad flash while
 * Play reconnects. Google Play remains authoritative: every successful owned
 * purchase query overwrites the cache, so refunds/revocations clear it again.
 */
class RemoveAdsBillingManager(
    private val activity: Activity,
) : PurchasesUpdatedListener {
    data class State(
        val hasRemovedAds: Boolean = false,
        val isConnecting: Boolean = true,
        val isBillingReady: Boolean = false,
        val isPurchasing: Boolean = false,
        val isPending: Boolean = false,
        val productAvailable: Boolean = false,
        val ownershipQueryCompleted: Boolean = false,
        val formattedPrice: String? = null,
        val errorMessage: String? = null,
    )

    companion object {
        const val PRODUCT_ID = "com.rsm.eztrivia.removeads"

        private const val PREFS_NAME = "eztrivia_purchases"
        private const val ENTITLEMENT_KEY = "purchase.removeAds.v1"
    }

    private val preferences = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        State(hasRemovedAds = preferences.getBoolean(ENTITLEMENT_KEY, false))
    )
    val state: StateFlow<State> = _state.asStateFlow()

    private var productDetails: ProductDetails? = null
    private var selectedOfferToken: String? = null
    private var started = false
    private var connecting = false

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
        if (started) return
        started = true
        connect()
    }

    fun refresh() {
        if (billingClient.isReady) {
            _state.value = _state.value.copy(isBillingReady = true)
            queryOwnedPurchases(showMissingMessage = false)
            queryProduct()
        } else {
            connect()
        }
    }

    fun purchase() {
        if (_state.value.hasRemovedAds || _state.value.isPurchasing || _state.value.isPending) return
        if (!billingClient.isReady) {
            _state.value = _state.value.copy(
                isPurchasing = false,
                errorMessage = "Google Play isn't connected. Try again.",
            )
            refresh()
            return
        }

        // ProductDetails and offer eligibility can go stale after Settings
        // loads. Google recommends a fresh query immediately before checkout.
        _state.value = _state.value.copy(isPurchasing = true, errorMessage = null)
        billingClient.queryProductDetailsAsync(removeAdsQuery()) { result, response ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _state.value = _state.value.copy(
                    isPurchasing = false,
                    errorMessage = billingFailure("Couldn't load Remove Ads", result),
                )
                return@queryProductDetailsAsync
            }

            val details = response.productDetailsList.firstOrNull { it.productId == PRODUCT_ID }
            val offer = details?.oneTimePurchaseOfferDetailsList?.firstOrNull()
                ?: details?.oneTimePurchaseOfferDetails
            val token = offer?.offerToken?.takeIf { it.isNotBlank() }
            if (details == null || token == null) {
                _state.value = _state.value.copy(
                    isPurchasing = false,
                    productAvailable = false,
                    formattedPrice = null,
                    errorMessage = unavailableProductMessage(response),
                )
                return@queryProductDetailsAsync
            }

            productDetails = details
            selectedOfferToken = token
            _state.value = _state.value.copy(
                productAvailable = true,
                formattedPrice = offer.formattedPrice,
            )
            activity.runOnUiThread {
                val params = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .setOfferToken(token)
                    .build()
                val launch = billingClient.launchBillingFlow(
                    activity,
                    BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(listOf(params))
                        .build(),
                )
                if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
                    _state.value = _state.value.copy(
                        isPurchasing = false,
                        errorMessage = billingFailure("Couldn't open Google Play checkout", launch),
                    )
                }
            }
        }
    }

    fun restore() {
        if (billingClient.isReady) {
            queryOwnedPurchases(showMissingMessage = true)
        } else {
            _state.value = _state.value.copy(
                isConnecting = true,
                isBillingReady = false,
                errorMessage = null,
            )
            connect(onConnected = { queryOwnedPurchases(showMissingMessage = true) })
        }
    }

    fun close() {
        billingClient.endConnection()
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: List<Purchase>?,
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                handlePurchases(purchases.orEmpty(), authoritative = false, showMissingMessage = false)
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // A previous purchase on this Google account is a restore, not a failed sale.
                queryOwnedPurchases(showMissingMessage = false)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _state.value = _state.value.copy(isPurchasing = false, errorMessage = null)
            }
            else -> {
                _state.value = _state.value.copy(
                    isPurchasing = false,
                    errorMessage = billingResult.debugMessage.ifBlank {
                        "Google Play could not complete the purchase."
                    },
                )
            }
        }
    }

    private fun connect(onConnected: (() -> Unit)? = null) {
        if (billingClient.isReady) {
            _state.value = _state.value.copy(isConnecting = false, isBillingReady = true)
            onConnected?.invoke()
            return
        }
        if (connecting) return
        connecting = true
        _state.value = _state.value.copy(isConnecting = true, isBillingReady = false)
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                connecting = false
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.value = _state.value.copy(
                        isConnecting = false,
                        isBillingReady = true,
                        errorMessage = null,
                    )
                    if (onConnected != null) onConnected() else queryOwnedPurchases(showMissingMessage = false)
                    queryProduct()
                } else {
                    _state.value = _state.value.copy(
                        isConnecting = false,
                        isBillingReady = false,
                        errorMessage = billingResult.debugMessage.ifBlank {
                            "Google Play Billing is unavailable right now."
                        },
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                connecting = false
                _state.value = _state.value.copy(
                    isConnecting = true,
                    isBillingReady = false,
                )
                // enableAutoServiceReconnection() handles the actual reconnect.
            }
        })
    }

    private fun removeAdsQuery(): QueryProductDetailsParams =
        QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

    private fun queryProduct() {
        billingClient.queryProductDetailsAsync(removeAdsQuery()) { result, response ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                productDetails = null
                selectedOfferToken = null
                _state.value = _state.value.copy(
                    productAvailable = false,
                    formattedPrice = null,
                    errorMessage = billingFailure("Couldn't load Remove Ads", result),
                )
                return@queryProductDetailsAsync
            }

            val details = response.productDetailsList.firstOrNull { it.productId == PRODUCT_ID }
            val offer = details?.oneTimePurchaseOfferDetailsList?.firstOrNull()
                ?: details?.oneTimePurchaseOfferDetails
            val token = offer?.offerToken?.takeIf { it.isNotBlank() }
            productDetails = details
            selectedOfferToken = token
            _state.value = _state.value.copy(
                productAvailable = details != null && token != null,
                formattedPrice = if (token == null) null else offer.formattedPrice,
                errorMessage = if (details == null || token == null)
                    unavailableProductMessage(response) else null,
            )
        }
    }

    /** Status codes identify catalog/region/test-account issues without a crash. */
    private fun unavailableProductMessage(response: com.android.billingclient.api.QueryProductDetailsResult): String {
        val reason = response.unfetchedProductList.firstOrNull {
            it.productId == PRODUCT_ID
        }?.statusCode
        return when (reason) {
            com.android.billingclient.api.UnfetchedProduct.StatusCode.NO_ELIGIBLE_OFFER ->
                "Remove Ads has no available offer for this Google Play account (offer status 4)."
            com.android.billingclient.api.UnfetchedProduct.StatusCode.PRODUCT_NOT_FOUND ->
                "Google Play can't find Remove Ads for this installation (product status 3)."
            com.android.billingclient.api.UnfetchedProduct.StatusCode.INVALID_PRODUCT_ID_FORMAT ->
                "Remove Ads product configuration needs attention (status 2)."
            else -> "Remove Ads is unavailable from Google Play for this account right now."
        }
    }

    private fun billingFailure(action: String, result: BillingResult): String =
        "$action (Google Play code ${result.responseCode}): " +
            result.debugMessage.ifBlank { "Try again or report a problem." }

    private fun queryOwnedPurchases(showMissingMessage: Boolean) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _state.value = _state.value.copy(ownershipQueryCompleted = true)
                handlePurchases(
                    purchases = purchases,
                    authoritative = true,
                    showMissingMessage = showMissingMessage,
                )
            } else {
                _state.value = _state.value.copy(
                    isPurchasing = false,
                    errorMessage = result.debugMessage.ifBlank {
                        "Google Play could not restore purchases."
                    },
                )
            }
        }
    }

    private fun handlePurchases(
        purchases: List<Purchase>,
        authoritative: Boolean,
        showMissingMessage: Boolean,
    ) {
        val matching = purchases.filter { PRODUCT_ID in it.products }
        val purchased = matching.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        val pending = matching.any { it.purchaseState == Purchase.PurchaseState.PENDING }

        if (purchased != null) {
            setEntitlement(true)
            _state.value = _state.value.copy(
                isPurchasing = false,
                isPending = false,
                errorMessage = null,
            )
            if (!purchased.isAcknowledged) {
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchased.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(params) { result ->
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        _state.value = _state.value.copy(
                            errorMessage = "Your purchase is active, but Google Play has not acknowledged it yet."
                        )
                    }
                }
            }
            return
        }

        if (authoritative) {
            setEntitlement(false)
        }
        _state.value = _state.value.copy(
            isPurchasing = false,
            isPending = pending,
            errorMessage = when {
                pending -> "Your Remove Ads purchase is pending in Google Play."
                showMissingMessage -> "No Remove Ads purchase was found for this Google Play account."
                else -> _state.value.errorMessage
            },
        )
    }

    private fun setEntitlement(value: Boolean) {
        preferences.edit().putBoolean(ENTITLEMENT_KEY, value).apply()
        if (_state.value.hasRemovedAds != value) {
            _state.value = _state.value.copy(hasRemovedAds = value)
        }
    }
}

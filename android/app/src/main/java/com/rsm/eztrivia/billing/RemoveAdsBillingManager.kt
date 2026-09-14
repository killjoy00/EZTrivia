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
        val isPurchasing: Boolean = false,
        val isPending: Boolean = false,
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
            queryOwnedPurchases(showMissingMessage = false)
            queryProduct()
        } else {
            connect()
        }
    }

    fun purchase() {
        val details = productDetails ?: run {
            _state.value = _state.value.copy(
                errorMessage = "Remove Ads is not available from Google Play right now."
            )
            refresh()
            return
        }
        if (_state.value.hasRemovedAds || _state.value.isPurchasing) return

        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        selectedOfferToken?.takeIf { it.isNotBlank() }?.let(productParamsBuilder::setOfferToken)

        _state.value = _state.value.copy(isPurchasing = true, errorMessage = null)
        val result = billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParamsBuilder.build()))
                .build(),
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.value = _state.value.copy(
                isPurchasing = false,
                errorMessage = result.debugMessage.ifBlank { "Google Play could not start the purchase." },
            )
        }
    }

    fun restore() {
        if (billingClient.isReady) {
            queryOwnedPurchases(showMissingMessage = true)
        } else {
            _state.value = _state.value.copy(
                isConnecting = true,
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
        purchases: MutableList<Purchase>?,
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                handlePurchases(purchases.orEmpty(), authoritative = false, showMissingMessage = false)
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
            _state.value = _state.value.copy(isConnecting = false)
            onConnected?.invoke()
            return
        }

        _state.value = _state.value.copy(isConnecting = true)
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.value = _state.value.copy(isConnecting = false, errorMessage = null)
                    queryOwnedPurchases(showMissingMessage = false)
                    queryProduct()
                    onConnected?.invoke()
                } else {
                    _state.value = _state.value.copy(
                        isConnecting = false,
                        errorMessage = billingResult.debugMessage.ifBlank {
                            "Google Play Billing is unavailable right now."
                        },
                    )
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.value = _state.value.copy(isConnecting = true)
                // enableAutoServiceReconnection() handles the actual reconnect.
            }
        })
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                productDetails = null
                selectedOfferToken = null
                _state.value = _state.value.copy(
                    formattedPrice = null,
                    errorMessage = result.debugMessage.ifBlank {
                        "Google Play could not load Remove Ads."
                    },
                )
                return@queryProductDetailsAsync
            }

            val details = queryResult.productDetailsList.firstOrNull { it.productId == PRODUCT_ID }
            productDetails = details
            val offer = details?.oneTimePurchaseOfferDetailsList?.firstOrNull()
                ?: details?.oneTimePurchaseOfferDetails
            selectedOfferToken = offer?.offerToken
            _state.value = _state.value.copy(
                formattedPrice = offer?.formattedPrice,
                errorMessage = if (details == null) {
                    "Remove Ads is not available in Google Play yet."
                } else {
                    null
                },
            )
        }
    }

    private fun queryOwnedPurchases(showMissingMessage: Boolean) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
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

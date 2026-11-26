package com.rsm.eztrivia.billing

import com.android.billingclient.api.UnfetchedProduct

/** Distinguish a missing Play product from an inactive offer or a temporary outage. */
internal object RemoveAdsCatalogMessages {
    fun forStatus(code: Int?): String = when (code) {
        UnfetchedProduct.StatusCode.NO_ELIGIBLE_OFFER ->
            "Remove Ads has no available offer for this Google Play account (offer status 4)."
        UnfetchedProduct.StatusCode.PRODUCT_NOT_FOUND ->
            "Google Play can't find Remove Ads for this installation (product status 3)."
        UnfetchedProduct.StatusCode.INVALID_PRODUCT_ID_FORMAT ->
            "Remove Ads product configuration needs attention (status 2)."
        else -> "Remove Ads is unavailable from Google Play for this account right now."
    }
}

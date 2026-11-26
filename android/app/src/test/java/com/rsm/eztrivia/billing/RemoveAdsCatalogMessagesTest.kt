package com.rsm.eztrivia.billing

import com.android.billingclient.api.UnfetchedProduct
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoveAdsCatalogMessagesTest {
    @Test
    fun noEligibleOfferHasSpecificAccountAvailabilityFeedback() {
        val message = RemoveAdsCatalogMessages.forStatus(UnfetchedProduct.StatusCode.NO_ELIGIBLE_OFFER)
        assertTrue(message.contains("no available offer"))
        assertTrue(message.contains("status 4"))
    }

    @Test
    fun missingCatalogProductIsDistinguishedFromNetworkFailure() {
        val message = RemoveAdsCatalogMessages.forStatus(UnfetchedProduct.StatusCode.PRODUCT_NOT_FOUND)
        assertTrue(message.contains("can't find"))
        assertTrue(message.contains("status 3"))
    }

    @Test
    fun otherFailuresRemainActionableWithoutInventingCheckoutSuccess() {
        assertTrue(RemoveAdsCatalogMessages.forStatus(null).contains("unavailable"))
    }
}

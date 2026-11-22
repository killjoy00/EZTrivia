package com.rsm.eztrivia.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoveAdsConnectionFeedbackTest {
    @Test
    fun timedOutConnectionStopsSpinnerAndAllowsRetry() {
        val before = RemoveAdsBillingManager.State(
            hasRemovedAds = false,
            isConnecting = true,
            isBillingReady = false,
        )
        val result = RemoveAdsConnectionFeedback.timedOut(before)

        assertFalse(result.isConnecting)
        assertFalse(result.isBillingReady)
        assertFalse(result.hasRemovedAds)
        assertTrue(result.errorMessage.orEmpty().contains("Try again"))
        assertTrue(RemoveAdsConnectionFeedback.CONNECTION_TIMEOUT_MS > 0)
    }

    @Test
    fun disconnectedServiceDoesNotClaimItIsStillConnecting() {
        val before = RemoveAdsBillingManager.State(
            hasRemovedAds = true,
            isConnecting = true,
            isBillingReady = true,
            formattedPrice = "$0.99",
        )
        val result = RemoveAdsConnectionFeedback.disconnected(before)

        assertFalse(result.isConnecting)
        assertFalse(result.isBillingReady)
        // A temporary Play disconnection must never remove a paid entitlement.
        assertTrue(result.hasRemovedAds)
        assertEquals("$0.99", result.formattedPrice)
        assertTrue(result.errorMessage.orEmpty().contains("Try again"))
    }
}

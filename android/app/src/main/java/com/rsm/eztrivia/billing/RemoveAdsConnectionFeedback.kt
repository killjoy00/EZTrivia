package com.rsm.eztrivia.billing

/** Prevent the purchase screen from getting stuck on a misleading spinner. */
internal object RemoveAdsConnectionFeedback {
    const val CONNECTION_TIMEOUT_MS = 15_000L

    fun timedOut(state: RemoveAdsBillingManager.State): RemoveAdsBillingManager.State =
        state.copy(
            isConnecting = false,
            isBillingReady = false,
            errorMessage = "Google Play Billing didn't respond. Check that the Play Store " +
                "is available, then tap Try again.",
        )

    fun disconnected(state: RemoveAdsBillingManager.State): RemoveAdsBillingManager.State =
        state.copy(
            isConnecting = false,
            isBillingReady = false,
            errorMessage = "Google Play Billing disconnected. Tap Try again to reconnect.",
        )
}

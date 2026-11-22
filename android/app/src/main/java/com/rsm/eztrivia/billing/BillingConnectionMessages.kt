package com.rsm.eztrivia.billing

/** Clear, actionable connection errors rather than a perpetual loading state. */
internal object BillingConnectionMessages {
    fun timeout(): String =
        "Google Play didn't respond within 15 seconds. Check that the Play Store is online " +
            "and signed in, then tap Try again."

    fun disconnected(): String =
        "Google Play Billing disconnected. Check the Play Store, then tap Try again."

    fun failed(code: Int?, debugMessage: String): String {
        val status = if (code == null) "" else " (code $code)"
        val detail = debugMessage.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
        return "Couldn't connect to Google Play Billing$status.$detail Tap Try again."
    }
}

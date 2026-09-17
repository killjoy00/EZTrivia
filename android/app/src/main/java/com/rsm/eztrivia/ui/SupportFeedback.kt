package com.rsm.eztrivia.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.rsm.eztrivia.BuildConfig
import com.rsm.eztrivia.ads.AdConsentManager
import com.rsm.eztrivia.billing.RemoveAdsBillingManager
import java.util.Locale

private const val SUPPORT_EMAIL = "killjoy00@yahoo.com"
private const val SUPPORT_URL = "https://killjoy00.github.io/EZTrivia/support.html"

enum class SupportMessageKind(
    val subject: String,
    val intro: String,
) {
    FEEDBACK(
        subject = "EZ Trivia feedback",
        intro = "Tell us what you like, dislike, or would change:",
    ),
    PROBLEM(
        subject = "EZ Trivia problem report",
        intro = "Please describe what happened and what you expected:",
    ),
}

/**
 * Opens a user-controlled email draft with enough environment information to
 * diagnose device-specific Android issues. Nothing is transmitted until the
 * user reviews and sends the draft from their email app.
 */
fun openSupportEmail(
    context: Context,
    kind: SupportMessageKind,
    purchaseState: RemoveAdsBillingManager.State?,
    consentState: AdConsentManager.State?,
) {
    val body = buildString {
        appendLine(kind.intro)
        appendLine()
        appendLine()
        appendLine("--- EZ Trivia diagnostics ---")
        appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Locale: ${Locale.getDefault().toLanguageTag()}")

        if (purchaseState != null) {
            appendLine(
                "Billing: removedAds=${purchaseState.hasRemovedAds}, " +
                    "connecting=${purchaseState.isConnecting}, " +
                    "purchasing=${purchaseState.isPurchasing}, " +
                    "pending=${purchaseState.isPending}, " +
                    "priceLoaded=${purchaseState.formattedPrice != null}"
            )
            purchaseState.errorMessage?.takeIf { it.isNotBlank() }?.let {
                appendLine("Billing message: $it")
            }
        } else {
            appendLine("Billing: unavailable")
        }

        if (consentState != null) {
            appendLine(
                "Ads/consent: canRequestAds=${consentState.canRequestAds}, " +
                    "privacyOptionsRequired=${consentState.privacyOptionsRequired}, " +
                    "checking=${consentState.isChecking}"
            )
            consentState.errorMessage?.takeIf { it.isNotBlank() }?.let {
                appendLine("Ad privacy message: $it")
            }
        } else {
            appendLine("Ads/consent: unavailable")
        }
    }

    val emailUri = Uri.parse("mailto:$SUPPORT_EMAIL")
        .buildUpon()
        .appendQueryParameter("subject", kind.subject)
        .appendQueryParameter("body", body)
        .build()
    val emailIntent = Intent(Intent.ACTION_SENDTO, emailUri)

    if (emailIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(emailIntent)
    } else {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_URL)))
    }
}

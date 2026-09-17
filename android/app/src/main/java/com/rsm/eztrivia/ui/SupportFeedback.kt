package com.rsm.eztrivia.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rsm.eztrivia.BuildConfig
import com.rsm.eztrivia.MainActivity
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

@Composable
fun SupportSettingsCard() {
    val context = LocalContext.current
    val activity = LocalActivity.current as? MainActivity
    val purchaseState by activity?.removeAdsBillingManager?.state?.collectAsState()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(null) }
    val consentState by activity?.adConsentManager?.state?.collectAsState()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(null) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "SUPPORT",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Send feedback or report a problem. The email draft includes the app version, Android version, device model, and current ads/purchase status so device-specific bugs are easier to diagnose.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        openSupportEmail(
                            context = context,
                            kind = SupportMessageKind.FEEDBACK,
                            purchaseState = purchaseState,
                            consentState = consentState,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Send feedback")
                }
                OutlinedButton(
                    onClick = {
                        openSupportEmail(
                            context = context,
                            kind = SupportMessageKind.PROBLEM,
                            purchaseState = purchaseState,
                            consentState = consentState,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Report a problem")
                }
                Text(
                    "Nothing is sent automatically. You can review or remove the diagnostics before sending the email.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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

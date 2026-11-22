package com.rsm.eztrivia.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rsm.eztrivia.MainActivity

/** Match the short iOS Remove Ads section; keep diagnostics in Report a problem. */
@Composable
fun MonetizationSettingsCard() {
    val activity = LocalActivity.current as? MainActivity
    val billing = activity?.removeAdsBillingManager
    val consent = activity?.adConsentManager
    val purchase = billing?.state?.collectAsState()?.value
    val privacy = consent?.state?.collectAsState()?.value

    // The player has navigated to Settings because they want to buy or restore.
    // A product lookup that failed at launch must be retryable without restarting.
    LaunchedEffect(billing) {
        if (billing != null && !billing.state.value.hasRemovedAds) billing.refresh()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Remove Ads",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp),
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when {
                        purchase == null -> Text("Google Play Billing is unavailable.")
                        purchase.hasRemovedAds ->
                            Text("✓ Ads removed. Thank you!", color = MaterialTheme.colorScheme.primary)
                        else -> {
                            val canBuy = purchase.isBillingReady && !purchase.isConnecting &&
                                !purchase.isPurchasing && !purchase.isPending &&
                                purchase.productAvailable && purchase.formattedPrice != null

                            if (canBuy) {
                                Button(
                                    onClick = { billing?.purchase() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Remove Ads — ${purchase.formattedPrice}") }
                            } else if (purchase.isPurchasing) {
                                Text("Opening Google Play…")
                            } else if (purchase.isPending) {
                                Text("Purchase pending in Google Play")
                            } else if (purchase.isConnecting) {
                                Text("Connecting to Google Play…")
                            } else if (purchase.isLoadingProduct) {
                                Text("Loading Remove Ads…")
                            } else {
                                Text("Remove Ads is unavailable right now.")
                                TextButton(onClick = { billing?.refresh() }) { Text("Try again") }
                            }

                            TextButton(
                                onClick = { billing?.restore() },
                                enabled = !purchase.isPurchasing,
                            ) { Text("Restore purchases") }
                        }
                    }
                    purchase?.errorMessage?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Text(
                "One-time purchase. Removes the banner on Android devices using the same Google Play account.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (purchase?.hasRemovedAds != true && privacy?.privacyOptionsRequired == true) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Advertising",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { consent?.showPrivacyOptions() },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    ) { Text("Ad privacy choices") }
                }
            }
        }

        SupportSettingsCard()
    }
}

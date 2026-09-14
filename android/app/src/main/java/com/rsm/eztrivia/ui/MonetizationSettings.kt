package com.rsm.eztrivia.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rsm.eztrivia.MainActivity

@Composable
fun MonetizationSettingsCard() {
    val activity = LocalActivity.current as? MainActivity
    val billingManager = activity?.removeAdsBillingManager
    val adConsentManager = activity?.adConsentManager
    val purchaseState = billingManager?.state?.collectAsState()?.value
    val consentState = adConsentManager?.state?.collectAsState()?.value

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "ADS & PRIVACY",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    purchaseState == null -> Text(
                        "Google Play purchases are unavailable in this activity.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    purchaseState.hasRemovedAds -> Text(
                        "Ads removed. Your one-time Google Play purchase is active.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> {
                        Text(
                            "EZ Trivia shows one banner while you browse the Play screen. A one-time purchase removes advertising.",
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { billingManager.purchase() },
                            enabled = !purchaseState.isConnecting &&
                                !purchaseState.isPurchasing &&
                                !purchaseState.isPending &&
                                purchaseState.formattedPrice != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        ) {
                            Text(
                                when {
                                    purchaseState.isPurchasing -> "Purchasing…"
                                    purchaseState.isPending -> "Purchase pending"
                                    purchaseState.formattedPrice != null ->
                                        "Remove Ads — ${purchaseState.formattedPrice}"
                                    else -> "Remove Ads unavailable"
                                }
                            )
                        }
                        OutlinedButton(
                            onClick = { billingManager.restore() },
                            enabled = !purchaseState.isPurchasing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        ) {
                            Text("Restore purchases")
                        }
                    }
                }

                if (purchaseState?.hasRemovedAds != true && consentState?.privacyOptionsRequired == true) {
                    HorizontalDivider()
                    OutlinedButton(
                        onClick = { adConsentManager.showPrivacyOptions() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Text("Ad privacy choices")
                    }
                }

                purchaseState?.errorMessage?.let { error ->
                    Text(
                        error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                consentState?.errorMessage?.let { error ->
                    Text(
                        "Ad privacy: $error",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    "Google Play handles payment details. Ad privacy choices are provided by Google's User Messaging Platform where required.",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

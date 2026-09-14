package com.rsm.eztrivia.ads

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.rsm.eztrivia.BuildConfig
import com.rsm.eztrivia.MainActivity

/** A 320x50 banner that disappears entirely when consent or entitlement forbids ads. */
@Composable
fun AdBanner(
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current as? MainActivity ?: return
    val consent by activity.adConsentManager.state.collectAsState()
    val purchase by activity.removeAdsBillingManager.state.collectAsState()

    if (!consent.canRequestAds || purchase.hasRemovedAds) return

    val adView = remember(activity, BuildConfig.ADMOB_BANNER_ID) {
        AdView(activity).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BuildConfig.ADMOB_BANNER_ID
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }

    AndroidView(
        factory = { adView },
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
    )
}

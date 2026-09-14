package com.rsm.eztrivia.ads

import android.app.Activity
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the Android UMP privacy flow and starts Google Mobile Ads only after
 * consent state says ads may be requested. A consent or ad-SDK failure never
 * blocks trivia gameplay; the banner simply stays absent.
 */
data class AdConsentState(
    val canRequestAds: Boolean = false,
    val privacyOptionsRequired: Boolean = false,
    val adsInitialized: Boolean = false,
    val errorMessage: String? = null,
)

class AdConsentManager(private val activity: Activity) {
    private val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
    private val _state = MutableStateFlow(AdConsentState())
    val state: StateFlow<AdConsentState> = _state.asStateFlow()

    private var configureStarted = false
    private var mobileAdsStarted = false

    fun configure() {
        if (configureStarted) {
            refreshState()
            return
        }
        configureStarted = true

        val parameters = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            parameters,
            {
                // Do not initialize ads from the freshly updated consent state
                // until UMP has finished the required-form step. When no form is
                // required this callback returns immediately, so this preserves
                // the fast path without racing a form that still needs to be shown.
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        recordError(formError.message)
                    }
                    refreshState()
                }
            },
            { requestError ->
                recordError(requestError.message)
                // canRequestAds may still be true from a prior valid consent
                // decision, so always refresh instead of treating the network
                // failure as a permanent ad ban.
                refreshState()
            },
        )
    }

    fun presentPrivacyOptions() {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                recordError(formError.message)
            }
            refreshState()
        }
    }

    private fun refreshState() {
        val canRequestAds = consentInformation.canRequestAds()
        val privacyRequired =
            consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

        _state.value = _state.value.copy(
            canRequestAds = canRequestAds,
            privacyOptionsRequired = privacyRequired,
        )

        if (canRequestAds && !mobileAdsStarted) {
            mobileAdsStarted = true
            MobileAds.initialize(activity.applicationContext) {
                _state.value = _state.value.copy(adsInitialized = true)
            }
        }
    }

    private fun recordError(message: String?) {
        if (!message.isNullOrBlank()) {
            _state.value = _state.value.copy(errorMessage = message)
        }
    }
}

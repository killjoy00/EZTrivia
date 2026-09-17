package com.rsm.eztrivia.ads

import android.app.Activity
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Handles UMP consent before the first ad request and exposes privacy choices. */
class AdConsentManager(
    private val activity: Activity,
) {
    enum class BannerStatus {
        NOT_REQUESTED,
        LOADING,
        LOADED,
        FAILED,
    }

    data class State(
        val canRequestAds: Boolean = false,
        val privacyOptionsRequired: Boolean = false,
        val isChecking: Boolean = true,
        val mobileAdsInitialized: Boolean = false,
        val bannerStatus: BannerStatus = BannerStatus.NOT_REQUESTED,
        val bannerErrorMessage: String? = null,
        val errorMessage: String? = null,
    )

    private val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var didInitializeAds = false
    private var configured = false

    fun configure() {
        if (configured) return
        configured = true

        val parameters = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            parameters,
            {
                refreshState()
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        _state.value = _state.value.copy(errorMessage = formError.message)
                    }
                    refreshState(isChecking = false)
                }
            },
            { requestError ->
                _state.value = _state.value.copy(errorMessage = requestError.message)
                // A previous valid consent choice may still allow requests even
                // if this launch's refresh failed, so always re-read SDK state.
                refreshState(isChecking = false)
            },
        )
    }

    fun showPrivacyOptions() {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                _state.value = _state.value.copy(errorMessage = formError.message)
            }
            refreshState(isChecking = false)
        }
    }

    fun markBannerLoading() {
        _state.value = _state.value.copy(
            bannerStatus = BannerStatus.LOADING,
            bannerErrorMessage = null,
        )
    }

    fun markBannerLoaded() {
        _state.value = _state.value.copy(
            bannerStatus = BannerStatus.LOADED,
            bannerErrorMessage = null,
        )
    }

    fun markBannerFailed(message: String) {
        _state.value = _state.value.copy(
            bannerStatus = BannerStatus.FAILED,
            bannerErrorMessage = message,
        )
    }

    private fun refreshState(isChecking: Boolean = _state.value.isChecking) {
        val canRequestAds = consentInformation.canRequestAds()
        val privacyRequired =
            consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

        _state.value = _state.value.copy(
            canRequestAds = canRequestAds,
            privacyOptionsRequired = privacyRequired,
            isChecking = isChecking,
        )

        if (canRequestAds && !didInitializeAds) {
            didInitializeAds = true
            MobileAds.initialize(activity.applicationContext) {
                _state.value = _state.value.copy(mobileAdsInitialized = true)
            }
        }
    }
}

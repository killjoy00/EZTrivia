import Foundation
#if canImport(AppTrackingTransparency)
import AppTrackingTransparency
#endif
#if canImport(GoogleMobileAds) && canImport(UserMessagingPlatform)
import GoogleMobileAds
import UserMessagingPlatform
#endif

@MainActor
final class AdConsentManager: ObservableObject {
    @Published private(set) var canRequestAds = false
    @Published private(set) var privacyOptionsRequired = false
    /// Kept for a future settings/debug surface. AdBannerView deliberately
    /// does not show this to players -- see its doc comment -- so failures
    /// are only visible via Telemetry.record below.
    @Published private(set) var errorMessage: String?
    private var didStartAds = false
    private var didBeginConsentFlow = false

    func configure() {
        guard !didBeginConsentFlow else { return }
        #if canImport(GoogleMobileAds) && canImport(UserMessagingPlatform)
        guard let appID = Bundle.main.object(forInfoDictionaryKey: "GADApplicationIdentifier") as? String,
              appID.hasPrefix("ca-app-pub-") else { return }
        didBeginConsentFlow = true
        let parameters = RequestParameters()
        ConsentInformation.shared.requestConsentInfoUpdate(with: parameters) { [weak self] error in
            Task { @MainActor [weak self] in
                guard let self else { return }
                if let error { self.record(error) }
                do { try await ConsentForm.loadAndPresentIfRequired(from: nil) }
                catch { self.record(error) }

                // The published European/US UMP forms handle regional consent.
                // If an AdMob IDFA explainer is also published, UMP may already
                // have shown Apple's system prompt. Check the current status to
                // avoid asking twice; otherwise show the native ATT request.
                await self.requestTrackingPermissionIfNeeded()
                self.refreshState()
            }
        }
        // Do not start Mobile Ads or request a banner from a stale UMP consent
        // state while a new consent form / ATT decision is still outstanding.
        #endif
    }

    func presentPrivacyOptions() async {
        #if canImport(UserMessagingPlatform)
        do { try await ConsentForm.presentPrivacyOptionsForm(from: nil) }
        catch { record(error) }
        refreshState()
        #endif
    }

    private func requestTrackingPermissionIfNeeded() async {
        #if canImport(AppTrackingTransparency) && canImport(UserMessagingPlatform)
        // No need to request access to the advertising identifier if UMP does
        // not permit an ad request. ATT never blocks gameplay or access to
        // contextual/non-personalized advertising when the user declines.
        guard ConsentInformation.shared.canRequestAds else { return }
        guard ATTrackingManager.trackingAuthorizationStatus == .notDetermined else { return }
        _ = await ATTrackingManager.requestTrackingAuthorization()
        #endif
    }

    private func refreshState() {
        #if canImport(GoogleMobileAds) && canImport(UserMessagingPlatform)
        canRequestAds = ConsentInformation.shared.canRequestAds
        privacyOptionsRequired = ConsentInformation.shared.privacyOptionsRequirementStatus == .required
        guard canRequestAds, !didStartAds else { return }
        didStartAds = true
        MobileAds.shared.start()
        #endif
    }

    private func record(_ error: Error) {
        errorMessage = error.localizedDescription
        Telemetry.record(error)
    }
}

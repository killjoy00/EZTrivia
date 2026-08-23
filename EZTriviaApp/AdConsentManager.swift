import Foundation
#if canImport(GoogleMobileAds) && canImport(UserMessagingPlatform)
import GoogleMobileAds
import UserMessagingPlatform
#endif

@MainActor
final class AdConsentManager: ObservableObject {
    @Published private(set) var canRequestAds = false
    @Published private(set) var privacyOptionsRequired = false
    @Published var errorMessage: String?
    private var didStartAds = false

    func configure() {
        #if canImport(GoogleMobileAds) && canImport(UserMessagingPlatform)
        guard let appID = Bundle.main.object(forInfoDictionaryKey: "GADApplicationIdentifier") as? String,
              appID.hasPrefix("ca-app-pub-") else { return }
        let parameters = RequestParameters()
        ConsentInformation.shared.requestConsentInfoUpdate(with: parameters) { [weak self] error in
            Task { @MainActor in
                if let error { self?.errorMessage = error.localizedDescription }
                do { try await ConsentForm.loadAndPresentIfRequired(from: nil) }
                catch { self?.errorMessage = error.localizedDescription }
                self?.refreshState()
            }
        }
        refreshState()
        #endif
    }

    func presentPrivacyOptions() async {
        #if canImport(UserMessagingPlatform)
        do { try await ConsentForm.presentPrivacyOptionsForm(from: nil) }
        catch { errorMessage = error.localizedDescription }
        refreshState()
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
}

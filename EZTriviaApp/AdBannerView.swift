import SwiftUI
#if canImport(GoogleMobileAds)
import GoogleMobileAds
#endif

struct AdBannerView: View {
    @EnvironmentObject private var adConsent: AdConsentManager
    private var adUnitID: String? {
        guard let value = Bundle.main.object(forInfoDictionaryKey: "EZTriviaAdMobBannerID") as? String,
              value.hasPrefix("ca-app-pub-") else { return nil }
        return value
    }

    var body: some View {
        Group {
            #if canImport(GoogleMobileAds)
            if let adUnitID, adConsent.canRequestAds {
                AdMobBannerRepresentable(adUnitID: adUnitID)
                    .frame(height: 50)
                    .accessibilityLabel("Advertisement")
            } else {
                adConfigurationMessage
            }
            #else
            adConfigurationMessage
            #endif
        }
        .frame(maxWidth: .infinity)
    }

    private var adConfigurationMessage: some View {
        Label(adStatusMessage, systemImage: adConsent.errorMessage == nil ? "rectangle.badge.plus" : "wifi.exclamationmark")
            .font(.caption)
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, minHeight: 50)
            .background(AppTheme.card, in: RoundedRectangle(cornerRadius: 12))
    }

    private var adStatusMessage: String {
        if let error = adConsent.errorMessage {
            return "Ads unavailable: \(error)"
        }
        return "Ad banner ready for AdMob configuration"
    }
}

#if canImport(GoogleMobileAds)
private struct AdMobBannerRepresentable: UIViewRepresentable {
    let adUnitID: String

    func makeUIView(context: Context) -> BannerView {
        let banner = BannerView(adSize: AdSizeBanner)
        banner.adUnitID = adUnitID
        banner.rootViewController = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow?.rootViewController }
            .first
        let request = Request()
        let extras = Extras()
        extras.additionalParameters = ["npa": "1"]
        request.register(extras)
        banner.load(request)
        return banner
    }

    func updateUIView(_ uiView: BannerView, context: Context) {}
}
#endif

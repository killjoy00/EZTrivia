import SwiftUI
#if canImport(GoogleMobileAds)
import GoogleMobileAds
#endif

/// The banner ad shown above the tab bar on the Play tab.
///
/// Renders nothing when an ad cannot be shown -- during the consent flow,
/// before a brand-new AdMob listing has been reviewed, or on any other
/// failure -- rather than a placeholder card. "Ad banner ready for AdMob
/// configuration" is developer-facing text; a reviewer or a real player
/// seeing it reads it as an unfinished app, not as a normal empty state.
/// Nothing is a much better empty state than a bug report shown to users.
struct AdBannerView: View {
    @EnvironmentObject private var adConsent: AdConsentManager
    private var adUnitID: String? {
        guard let value = Bundle.main.object(forInfoDictionaryKey: "EZTriviaAdMobBannerID") as? String,
              value.hasPrefix("ca-app-pub-") else { return nil }
        return value
    }

    var body: some View {
        #if canImport(GoogleMobileAds)
        if let adUnitID, adConsent.canRequestAds {
            AdMobBannerRepresentable(adUnitID: adUnitID)
                .frame(height: 50)
                .frame(maxWidth: .infinity)
                // Padding and the background live here, inside the branch
                // that only exists when there is a real ad to show. Putting
                // them on the call site instead would paint a visible empty
                // bar whenever there is nothing to show, which is exactly
                // the placeholder look this was meant to remove.
                .padding(.horizontal)
                .padding(.vertical, 8)
                .background(.bar)
                .accessibilityLabel("Advertisement")
        }
        #endif
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
        // No manual "npa" override here. The UMP consent flow that
        // AdConsentManager runs already tells the Google Mobile Ads SDK what
        // the player has consented to; forcing npa=1 on every request
        // overrode that regardless of consent or region, which meant the
        // whole app ran on the lower-earning non-personalized rate by
        // accident. A plain request lets the SDK honour the consent it
        // already has.
        banner.load(Request())
        return banner
    }

    func updateUIView(_ uiView: BannerView, context: Context) {}
}
#endif

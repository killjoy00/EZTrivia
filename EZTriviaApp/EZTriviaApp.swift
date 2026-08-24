import SwiftUI
@main
struct EZTriviaApp: App {
    @StateObject private var scores = ScoreStore()
    @StateObject private var gameCenter = GameCenterManager()
    @StateObject private var adConsent = AdConsentManager()

    init() {
        Telemetry.configure()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(scores)
                .environmentObject(gameCenter)
                .environmentObject(adConsent)
                .tint(.indigo)
                .onAppear {
                    gameCenter.authenticate()
                    adConsent.configure()
                }
        }
    }
}

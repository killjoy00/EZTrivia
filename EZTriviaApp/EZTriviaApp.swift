import SwiftUI
@main
struct EZTriviaApp: App {
    @StateObject private var scores = ScoreStore()
    @StateObject private var gameCenter = GameCenterManager()
    @StateObject private var adConsent = AdConsentManager()
    @StateObject private var feedback = Feedback()
    @StateObject private var settings = AppSettings()

    init() {
        Telemetry.configure()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(scores)
                .environmentObject(gameCenter)
                .environmentObject(adConsent)
                .environmentObject(feedback)
                .environmentObject(settings)
                .tint(.indigo)
                .onAppear {
                    gameCenter.authenticate()
                    adConsent.configure()
                }
        }
    }
}

import SwiftUI
@main
struct EZTriviaApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var scores = ScoreStore()
    @StateObject private var gameCenter = GameCenterManager()
    @StateObject private var adConsent = AdConsentManager()
    @StateObject private var feedback = Feedback()
    @StateObject private var settings = AppSettings()
    @StateObject private var streakReminder = StreakReminder()

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
                .environmentObject(streakReminder)
                .tint(.indigo)
                .onAppear {
                    gameCenter.authenticate()
                    adConsent.configure()
                }
                // Rescheduled on the way out as well as on the way in: the
                // reminder is a promise about a day that may turn over while
                // the app is closed, and leaving is the last chance to correct
                // what is queued before that happens.
                .onChange(of: scenePhase) { _, phase in
                    guard phase == .active || phase == .background else { return }
                    Task {
                        await streakReminder.refresh(
                            playedDays: Set(scores.dailyResults.keys)
                        )
                    }
                }
        }
    }
}

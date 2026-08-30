import SwiftUI

@MainActor
final class AppSettings: ObservableObject {
    static let autoAdvanceRange = 2...15

    @Published var autoAdvanceEnabled: Bool {
        didSet { defaults.set(autoAdvanceEnabled, forKey: Keys.autoAdvanceEnabled) }
    }

    @Published var autoAdvanceSeconds: Int {
        didSet {
            let clamped = min(max(autoAdvanceSeconds, Self.autoAdvanceRange.lowerBound), Self.autoAdvanceRange.upperBound)
            if autoAdvanceSeconds != clamped {
                autoAdvanceSeconds = clamped
            } else {
                defaults.set(autoAdvanceSeconds, forKey: Keys.autoAdvanceSeconds)
            }
        }
    }

    private enum Keys {
        static let autoAdvanceEnabled = "gameplay.autoAdvance.enabled"
        static let autoAdvanceSeconds = "gameplay.autoAdvance.seconds"
    }

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        autoAdvanceEnabled = defaults.bool(forKey: Keys.autoAdvanceEnabled)
        let storedSeconds = defaults.object(forKey: Keys.autoAdvanceSeconds) as? Int ?? 5
        autoAdvanceSeconds = min(
            max(storedSeconds, Self.autoAdvanceRange.lowerBound),
            Self.autoAdvanceRange.upperBound
        )
    }
}

struct SettingsView: View {
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var feedback: Feedback
    @EnvironmentObject private var adConsent: AdConsentManager
    @EnvironmentObject private var streakReminder: StreakReminder
    @EnvironmentObject private var scores: ScoreStore

    var body: some View {
        Form {
            Section {
                Toggle("Auto-advance", isOn: $settings.autoAdvanceEnabled)
                if settings.autoAdvanceEnabled {
                    Stepper(
                        value: $settings.autoAdvanceSeconds,
                        in: AppSettings.autoAdvanceRange
                    ) {
                        LabeledContent(
                            "Delay",
                            value: "\(settings.autoAdvanceSeconds) seconds"
                        )
                    }
                }
            } header: {
                Text("Gameplay")
            } footer: {
                Text("After an answer is revealed, the app waits for this delay before moving on. The Next button always remains available.")
            }

            Section {
                Toggle("Streak reminders", isOn: $streakReminder.isEnabled)
            } header: {
                Text("Daily Challenge")
            } footer: {
                if streakReminder.isEnabled && streakReminder.authorizationDenied {
                    Text("Notifications are turned off for EZ Trivia. Enable them in the Settings app to receive streak reminders.")
                } else {
                    Text("If a streak of \(StreakReminder.minimumStreak) days or more is still unplayed, a reminder arrives that evening. Nothing is sent otherwise.")
                }
            }

            Section("Feedback") {
                Toggle("Sound effects", isOn: $feedback.soundEnabled)
                Toggle("Haptics", isOn: $feedback.hapticsEnabled)
            }

            if adConsent.privacyOptionsRequired {
                Section("Advertising") {
                    Button("Ad privacy choices") {
                        Task { await adConsent.presentPrivacyOptions() }
                    }
                }
            }
        }
        .navigationTitle("Settings")
        .onChange(of: streakReminder.isEnabled) { _, _ in
            Task {
                await streakReminder.applyEnabledChange(
                    playedDays: Set(scores.dailyResults.keys)
                )
            }
        }
    }
}

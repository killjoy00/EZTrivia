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
    }
}

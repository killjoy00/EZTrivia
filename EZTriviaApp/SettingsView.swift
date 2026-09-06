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
    /// Formatted through `DateFormatter` rather than composed by hand so a
    /// 24-hour locale shows 20:00 instead of an English "8 PM".
    private static func hourLabel(_ hour: Int) -> String {
        var components = DateComponents()
        components.hour = hour
        components.minute = 0
        guard let date = Calendar.current.date(from: components) else { return "\(hour):00" }
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        formatter.dateStyle = .none
        return formatter.string(from: date)
    }

    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var feedback: Feedback
    @EnvironmentObject private var adConsent: AdConsentManager
    @EnvironmentObject private var streakReminder: StreakReminder
    @EnvironmentObject private var scores: ScoreStore
    @EnvironmentObject private var purchases: PurchaseStore

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
                if streakReminder.isEnabled {
                    Picker("Remind me at", selection: $streakReminder.reminderHour) {
                        ForEach(StreakReminder.selectableHours, id: \.self) { hour in
                            Text(Self.hourLabel(hour)).tag(hour)
                        }
                    }
                }
            } header: {
                Text("Daily Challenge")
            } footer: {
                if streakReminder.isEnabled && streakReminder.authorizationDenied {
                    Text("Notifications are turned off for EZ Trivia. Enable them in the Settings app to receive streak reminders.")
                } else {
                    Text("If a streak of \(StreakReminder.minimumStreak) days or more is still unplayed, a reminder arrives at this time. Nothing is sent otherwise.")
                }
            }

            Section("Feedback") {
                Toggle("Sound effects", isOn: $feedback.soundEnabled)
                Toggle("Haptics", isOn: $feedback.hapticsEnabled)
            }

            Section {
                Label("Player progress syncs automatically", systemImage: "icloud.fill")
                Text("Scores, Daily and Friend Challenge history, Quick Play, lifetime points, seen questions, and achievement progress are included.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } header: {
                Text("iCloud")
            } footer: {
                Text("When iCloud is available, EZ Trivia keeps this private gameplay state in sync across devices signed in to the same iCloud account. The game still works locally when iCloud is unavailable.")
            }

            Section {
                if purchases.hasRemovedAds {
                    Label("Ads removed. Thank you!", systemImage: "checkmark.seal.fill")
                        .foregroundStyle(.green)
                } else {
                    if let product = purchases.removeAdsProduct {
                        Button {
                            Task { await purchases.purchase() }
                        } label: {
                            LabeledContent("Remove Ads", value: product.displayPrice)
                        }
                        .disabled(purchases.isPurchasing)
                    } else {
                        // The product can be missing for reasons that are not
                        // the player's problem -- no network, or App Store
                        // Connect not finished propagating a new listing --
                        // so Restore stays available either way.
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Remove Ads is unavailable right now.")
                            if case let .requestFailed(message) = purchases.loadFailure {
                                Text(message).font(.caption)
                            }
                        }
                        .foregroundStyle(.secondary)
                    }
                    Button("Restore purchases") {
                        Task { await purchases.restore() }
                    }
                    .disabled(purchases.isPurchasing)
                }
            } header: {
                Text("Remove Ads")
            } footer: {
                Text("A one-time purchase that removes the banner everywhere in EZ Trivia. It applies to every device signed in to the same Apple Account, and nothing else about the game changes.")
            }

            if adConsent.privacyOptionsRequired && !purchases.hasRemovedAds {
                Section("Advertising") {
                    Button("Ad privacy choices") {
                        Task { await adConsent.presentPrivacyOptions() }
                    }
                }
            }

            Section {
                if let url = ReviewPrompt.writeReviewURL {
                    Link("Rate EZ Trivia", destination: url)
                }
            } footer: {
                Text("Ratings help other players find the app.")
            }
        }
        .navigationTitle("Settings")
        .alert("Purchase", isPresented: Binding(
            get: { purchases.errorMessage != nil },
            set: { if !$0 { purchases.errorMessage = nil } }
        )) { Button("OK") { purchases.errorMessage = nil } } message: {
            Text(purchases.errorMessage ?? "")
        }
        // Both the switch and the hour change what is queued, so both reschedule.
        .onChange(of: streakReminder.reminderHour) { _, _ in
            Task { await streakReminder.refresh(playedDays: Set(scores.dailyResults.keys)) }
        }
        .onChange(of: streakReminder.isEnabled) { _, _ in
            Task {
                await streakReminder.applyEnabledChange(
                    playedDays: Set(scores.dailyResults.keys)
                )
            }
        }
    }
}

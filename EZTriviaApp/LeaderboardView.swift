import SwiftUI

struct LeaderboardView: View {
    @EnvironmentObject private var scores: ScoreStore
    @EnvironmentObject private var gameCenter: GameCenterManager
    @EnvironmentObject private var adConsent: AdConsentManager
    @EnvironmentObject private var feedback: Feedback
    @State private var showClearConfirmation = false
    @State private var showGameCenter = false

    var body: some View {
        // A List rather than a bare ContentUnavailableView when empty: the
        // sound and haptics toggles have to be reachable before a player has
        // recorded a single score, which the old empty state made impossible.
        List {
            if scores.entries.isEmpty {
                Section {
                    ContentUnavailableView(
                        "No scores yet",
                        systemImage: "trophy",
                        description: Text("Finish a round and your best runs will appear here.")
                    )
                }
            } else {
                Section("Personal leaderboard") {
                    ForEach(Array(scores.entries.enumerated()), id: \.element.id) { rank, entry in
                        HStack(spacing: 14) {
                            Text("\(rank + 1)").font(.headline.monospacedDigit()).foregroundStyle(.secondary).frame(width: 28)
                            Image(systemName: entry.category.symbol).foregroundStyle(AppTheme.color(for: entry.category)).frame(width: 30)
                            VStack(alignment: .leading) {
                                Text(entry.category.title).font(.headline)
                                HStack(spacing: 4) {
                                    if let difficulty = entry.difficulty {
                                        Label(difficulty.title, systemImage: difficulty.symbol)
                                    }
                                    Text(entry.date, style: .date)
                                }
                                .font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Text("\(entry.score)/\(entry.total)").font(.title3.bold().monospacedDigit())
                        }
                        .padding(.vertical, 5)
                    }
                }
            }
            feedbackSection
        }
        .navigationTitle("Leaderboard")
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { showGameCenter = true } label: {
                    Label("Game Center", systemImage: "person.3.fill")
                }
                .disabled(!gameCenter.isAuthenticated)
                if adConsent.privacyOptionsRequired {
                    Button { Task { await adConsent.presentPrivacyOptions() } } label: {
                        Label("Ad privacy", systemImage: "hand.raised.fill")
                    }
                }
                if !scores.entries.isEmpty { Button("Clear") { showClearConfirmation = true } }
            }
        }
        .sheet(isPresented: $showGameCenter) { GameCenterDashboard().ignoresSafeArea() }
        .confirmationDialog("Clear all scores?", isPresented: $showClearConfirmation) {
            Button("Clear Scores", role: .destructive) { scores.clear() }
            Button("Cancel", role: .cancel) {}
        }
    }

    private var feedbackSection: some View {
        Section("Feedback") {
            Toggle("Sound effects", isOn: $feedback.soundEnabled)
            Toggle("Haptics", isOn: $feedback.hapticsEnabled)
        }
    }
}

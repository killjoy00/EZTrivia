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
                        "No rounds yet",
                        systemImage: "trophy",
                        description: Text("Finish a round and it will appear here.")
                    )
                }
            } else {
                // "Recent rounds", not "Personal leaderboard", and no rank
                // column: these are ordered by when they were played, so a
                // position number would imply a ranking the list no longer
                // carries.
                Section("Recent rounds") {
                    ForEach(scores.entries) { entry in
                        HStack(spacing: 14) {
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
            lifetimeSection
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

    /// Lifetime points, which is what the Game Center category boards now
    /// rank. Without this the new scoring would be invisible in the app: a
    /// player would only ever see it by opening the Game Center dashboard.
    @ViewBuilder
    private var lifetimeSection: some View {
        // A plain [TriviaCategory] rather than an array of pairs: Swift has no
        // key paths into tuples, so `ForEach(pairs, id: \.0)` would not compile.
        // TriviaCategory is already Identifiable, so this needs no id at all.
        let earned = TriviaCategory.allCases
            .filter { scores.lifetimePoints(for: $0) > 0 }
            .sorted { scores.lifetimePoints(for: $0) > scores.lifetimePoints(for: $1) }

        if !earned.isEmpty {
            Section {
                ForEach(earned) { category in
                    HStack(spacing: 14) {
                        Image(systemName: category.symbol)
                            .foregroundStyle(AppTheme.color(for: category))
                            .frame(width: 30)
                        Text(category.title)
                        Spacer()
                        Text(scores.lifetimePoints(for: category).formatted())
                            .font(.body.bold().monospacedDigit())
                    }
                }
            } header: {
                Text("Lifetime points")
            } footer: {
                Text("Harder questions are worth more. This total is what the Game Center leaderboards rank, and it only ever goes up.")
            }
        }
    }

    private var feedbackSection: some View {
        Section("Feedback") {
            Toggle("Sound effects", isOn: $feedback.soundEnabled)
            Toggle("Haptics", isOn: $feedback.hapticsEnabled)
        }
    }
}

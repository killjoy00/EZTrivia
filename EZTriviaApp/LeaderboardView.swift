import SwiftUI

struct LeaderboardView: View {
    @EnvironmentObject private var scores: ScoreStore
    @EnvironmentObject private var gameCenter: GameCenterManager
    @State private var showClearConfirmation = false
    @State private var showFullGameCenter = false
    @State private var showAllRounds = false

    private var visibleEntries: [LeaderboardEntry] {
        Array(scores.entries.prefix(showAllRounds ? scores.entries.count : 10))
    }

    private var visibleQuickPlayResults: [QuickPlayResult] {
        Array(scores.quickPlayResults.prefix(10))
    }

    var body: some View {
        List {
            // First, ahead of the local history. Leaderboards and achievements
            // are the reason to open this tab; recent rounds and lifetime
            // points are the record of how you got there.
            gameCenterSection

            if scores.entries.isEmpty && scores.quickPlayResults.isEmpty {
                Section {
                    ContentUnavailableView(
                        "No rounds yet",
                        systemImage: "trophy",
                        description: Text("Finish a category round or Quick Play and it will appear here.")
                    )
                }
            }

            if !scores.entries.isEmpty {
                Section("Recent category rounds") {
                    ForEach(visibleEntries) { entry in
                        recentRound(entry)
                    }
                    if scores.entries.count > 10 {
                        Button {
                            withAnimation { showAllRounds.toggle() }
                        } label: {
                            Label(
                                showAllRounds ? "Show less" : "Show more",
                                systemImage: showAllRounds ? "chevron.up" : "chevron.down"
                            )
                            .frame(maxWidth: .infinity, alignment: .center)
                        }
                    }
                }
            }

            if !visibleQuickPlayResults.isEmpty {
                Section("Recent Quick Play") {
                    ForEach(visibleQuickPlayResults) { result in
                        quickPlayRound(result)
                    }
                }
            }

            lifetimeSection
        }
        .navigationTitle("Scores")
        .toolbar {
            if !scores.entries.isEmpty {
                Button("Clear") { showClearConfirmation = true }
            }
        }
        .sheet(isPresented: $showFullGameCenter) {
            GameCenterDashboard().ignoresSafeArea()
        }
        .confirmationDialog("Clear recent rounds?", isPresented: $showClearConfirmation) {
            Button("Clear Recent Rounds", role: .destructive) { scores.clear() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Lifetime points, Daily Challenge history, Friend Challenges, Quick Play history, and achievements will be kept.")
        }
    }

    private func recentRound(_ entry: LeaderboardEntry) -> some View {
        HStack(spacing: 14) {
            Image(systemName: entry.category.symbol)
                .foregroundStyle(AppTheme.color(for: entry.category))
                .frame(width: 30)
            VStack(alignment: .leading) {
                Text(entry.category.title).font(.headline)
                HStack(spacing: 4) {
                    if let difficulty = entry.difficulty {
                        Label(difficulty.title, systemImage: difficulty.symbol)
                    }
                    Text(entry.date, style: .date)
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            }
            Spacer()
            Text("\(entry.score)/\(entry.total)")
                .font(.title3.bold().monospacedDigit())
        }
        .padding(.vertical, 5)
    }

    private func quickPlayRound(_ result: QuickPlayResult) -> some View {
        HStack(spacing: 14) {
            Image(systemName: "shuffle")
                .foregroundStyle(.indigo)
                .frame(width: 30)
            VStack(alignment: .leading) {
                Text("Quick Play").font(.headline)
                HStack(spacing: 4) {
                    Text(result.date, style: .date)
                    Text("·")
                    Text("\(result.points.formatted()) pts")
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            }
            Spacer()
            Text("\(result.score)/\(result.total)")
                .font(.title3.bold().monospacedDigit())
        }
        .padding(.vertical, 5)
    }

    @ViewBuilder
    private var lifetimeSection: some View {
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
                Text("Harder questions are worth more. These totals are what the category leaderboards rank.")
            }
        }
    }

    private var gameCenterSection: some View {
        Section("Game Center") {
            NavigationLink {
                GameCenterLeaderboardsView()
            } label: {
                Label("Leaderboards", systemImage: "list.number")
            }

            NavigationLink {
                AchievementsView()
            } label: {
                HStack {
                    Label("Achievements", systemImage: "medal.fill")
                    Spacer()
                    Text("\(completedAchievementCount)/\(AchievementCatalog.all.count)")
                        .foregroundStyle(.secondary)
                        .monospacedDigit()
                }
            }

            if gameCenter.isAuthenticated {
                Button { showFullGameCenter = true } label: {
                    Label("Open full Game Center", systemImage: "person.3.fill")
                }
            } else {
                Label("Game Center is not signed in", systemImage: "person.crop.circle.badge.exclamationmark")
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var completedAchievementCount: Int {
        let local = AchievementCatalog.progress(using: scores)
        return AchievementCatalog.all.count { achievement in
            let remote = achievement.isGameCenterAchievement
                ? gameCenter.achievementProgressByID[achievement.id] ?? 0
                : 0
            return max(local[achievement.id] ?? 0, remote) >= 100
        }
    }
}

/// A native Game Center reader. It uses the same GameKit leaderboards as the
/// Apple dashboard but presents them within EZ Trivia's navigation and tabs.
struct GameCenterLeaderboardsView: View {
    @EnvironmentObject private var gameCenter: GameCenterManager
    @State private var selectedBoard = GameCenterBoard.all[0]
    @State private var selectedScope = GameCenterPlayerScope.global
    @State private var snapshot: GameCenterLeaderboardSnapshot?
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var showFullGameCenter = false

    var body: some View {
        List {
            Section {
                Picker("Leaderboard", selection: $selectedBoard) {
                    ForEach(GameCenterBoard.all) { board in
                        Text(board.title).tag(board)
                    }
                }
                Picker("Players", selection: $selectedScope) {
                    ForEach(GameCenterPlayerScope.allCases) { scope in
                        Text(scope.title).tag(scope)
                    }
                }
                .pickerStyle(.segmented)
            }

            if !gameCenter.isAuthenticated {
                Section {
                    ContentUnavailableView(
                        "Game Center unavailable",
                        systemImage: "person.crop.circle.badge.exclamationmark",
                        description: Text("Sign in to Game Center, then return here to see rankings.")
                    )
                }
            } else if isLoading && snapshot == nil {
                Section {
                    HStack {
                        Spacer()
                        ProgressView("Loading rankings…")
                        Spacer()
                    }
                }
            } else if let errorMessage {
                Section {
                    ContentUnavailableView {
                        Label("Couldn’t load rankings", systemImage: "wifi.exclamationmark")
                    } description: {
                        Text(errorMessage)
                    } actions: {
                        Button("Try Again") { Task { await load() } }
                    }
                }
            } else if let snapshot {
                if let localPlayer = snapshot.localPlayer {
                    Section("Your rank") {
                        leaderboardRow(localPlayer)
                    }
                }

                Section {
                    if snapshot.leaders.isEmpty {
                        ContentUnavailableView(
                            "No scores yet",
                            systemImage: "trophy",
                            description: Text(selectedScope == .friends
                                ? "No Game Center friends have posted a score here yet."
                                : "Be the first player to post a score.")
                        )
                    } else {
                        ForEach(snapshot.leaders) { entry in
                            leaderboardRow(entry)
                        }
                    }
                } header: {
                    Text(selectedScope == .global ? "Top players" : "Friends")
                } footer: {
                    if snapshot.totalPlayerCount > 0 {
                        Text("\(snapshot.totalPlayerCount.formatted()) ranked players")
                    }
                }
            }
        }
        .navigationTitle("Leaderboards")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if gameCenter.isAuthenticated {
                Button { showFullGameCenter = true } label: {
                    Label("Open full Game Center", systemImage: "arrow.up.forward.app")
                }
            }
        }
        .sheet(isPresented: $showFullGameCenter) {
            GameCenterDashboard().ignoresSafeArea()
        }
        .task(id: LoadRequest(boardID: selectedBoard.id, scope: selectedScope)) {
            await load()
        }
        .refreshable { await load() }
    }

    private func leaderboardRow(_ entry: GameCenterLeaderboardRow) -> some View {
        HStack(spacing: 12) {
            Text("#\(entry.rank)")
                .font(.body.bold().monospacedDigit())
                .foregroundStyle(entry.isLocalPlayer ? .indigo : .secondary)
                .frame(width: 44, alignment: .leading)
            Text(entry.playerName)
                .fontWeight(entry.isLocalPlayer ? .semibold : .regular)
                .lineLimit(1)
            Spacer()
            Text(entry.formattedScore)
                .font(.body.bold().monospacedDigit())
        }
        .accessibilityElement(children: .combine)
    }

    @MainActor
    private func load() async {
        guard gameCenter.isAuthenticated else {
            snapshot = nil
            errorMessage = nil
            return
        }
        isLoading = true
        errorMessage = nil
        do {
            snapshot = try await gameCenter.loadLeaderboard(selectedBoard, scope: selectedScope)
        } catch {
            snapshot = nil
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
}

private struct LoadRequest: Hashable {
    let boardID: String
    let scope: GameCenterPlayerScope
}

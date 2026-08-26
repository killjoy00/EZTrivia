import SwiftUI

struct RootView: View {
    @EnvironmentObject private var gameCenter: GameCenterManager

    var body: some View {
        TabView {
            NavigationStack { HomeView() }
                .tabItem { Label("Play", systemImage: "play.fill") }
            NavigationStack { LeaderboardView() }
                .tabItem { Label("Scores", systemImage: "trophy.fill") }
        }
        .sheet(isPresented: Binding(
            get: { gameCenter.authenticationViewController != nil },
            set: { if !$0 { gameCenter.authenticationViewController = nil } }
        )) {
            if let controller = gameCenter.authenticationViewController {
                GameCenterAuthenticationView(viewController: controller) {
                    gameCenter.authenticationViewController = nil
                }
            }
        }
        .alert("Game Center", isPresented: Binding(
            get: { gameCenter.errorMessage != nil },
            set: { if !$0 { gameCenter.errorMessage = nil } }
        )) { Button("OK") { gameCenter.errorMessage = nil } } message: {
            Text(gameCenter.errorMessage ?? "An unknown error occurred.")
        }
    }
}

struct HomeView: View {
    private let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                header
                Text("Choose a category")
                    .font(.title2.bold())
                    .accessibilityAddTraits(.isHeader)
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(TriviaCategory.allCases) { category in
                        NavigationLink(value: category) { CategoryCard(category: category) }
                            .buttonStyle(.plain)
                    }
                }
                AdBannerView()
            }
            .padding()
        }
        .background(AppTheme.background)
        .navigationDestination(for: TriviaCategory.self) { DifficultyView(category: $0) }
        .navigationTitle("EZ Trivia")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var header: some View {
        HStack(spacing: 16) {
            Image(systemName: "brain.head.profile.fill")
                .font(.system(size: 38))
                .foregroundStyle(.white)
                .frame(width: 68, height: 68)
                .background(.white.opacity(0.18), in: RoundedRectangle(cornerRadius: 18))
            VStack(alignment: .leading, spacing: 3) {
                Text("Ready to play?").font(.title2.bold())
                Text("Ten quick questions. One great score.").font(.subheadline).opacity(0.9)
            }
        }
        .foregroundStyle(.white)
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppTheme.gradient, in: RoundedRectangle(cornerRadius: 26, style: .continuous))
    }
}

private struct DifficultyView: View {
    let category: TriviaCategory

    private var availabilityMessage: String {
        let counts = TriviaDifficulty.allCases.map {
            QuestionPicker.availableCount(category: category, difficulty: $0)
        }
        let total = counts.reduce(0, +)
        return "\(total) questions across three difficulties, served 10 at a time."
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Choose your challenge").font(.title.bold()).accessibilityAddTraits(.isHeader)
            Text(availabilityMessage).foregroundStyle(.secondary)
            ForEach(TriviaDifficulty.allCases) { difficulty in
                NavigationLink {
                    GameView(category: category, difficulty: difficulty)
                } label: {
                    HStack(spacing: 16) {
                        Image(systemName: difficulty.symbol).font(.title2).foregroundStyle(AppTheme.color(for: category)).frame(width: 44, height: 44).background(AppTheme.color(for: category).opacity(0.12), in: Circle())
                        VStack(alignment: .leading) {
                            Text(difficulty.title).font(.headline)
                            Text(difficulty.subtitle).font(.subheadline).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").foregroundStyle(.tertiary)
                    }
                    .foregroundStyle(.primary).cardStyle()
                }
                .buttonStyle(.plain)
            }
            Spacer()
        }
        .padding().background(AppTheme.background.ignoresSafeArea()).navigationTitle(category.title).navigationBarTitleDisplayMode(.inline)
    }
}

private struct CategoryCard: View {
    @EnvironmentObject private var scores: ScoreStore
    let category: TriviaCategory

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Image(systemName: category.symbol)
                .font(.title2.bold())
                .foregroundStyle(.white)
                .frame(width: 44, height: 44)
                .background(AppTheme.color(for: category), in: RoundedRectangle(cornerRadius: 13))
            Text(category.title).font(.headline).foregroundStyle(.primary)
            Text(category.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            HStack(spacing: 8) {
                ForEach(TriviaDifficulty.allCases) { difficulty in
                    Label {
                        Text(scores.bestScore(for: category, difficulty: difficulty).map { "\($0)%" } ?? "—")
                    } icon: {
                        Image(systemName: difficulty.symbol)
                    }
                    .accessibilityLabel("\(difficulty.title) best \(scores.bestScore(for: category, difficulty: difficulty).map { "\($0) percent" } ?? "not played")")
                }
            }
            .font(.caption2.bold().monospacedDigit())
            .foregroundStyle(AppTheme.color(for: category))
        }
        .frame(maxWidth: .infinity, minHeight: 154, alignment: .leading)
        .cardStyle()
        .accessibilityElement(children: .combine)
        .accessibilityHint("Starts a ten-question round")
    }
}

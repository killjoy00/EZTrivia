import SwiftUI

/// A pushed round, addressed by value.
///
/// Value-based rather than a `NavigationLink(destination:)` because the
/// result screen needs to return the player all the way to the category
/// grid, and only a path the app owns can be truncated like that. A
/// destination-based link pushes a screen the NavigationPath does not track,
/// which is why "Back to categories" previously landed on the difficulty
/// picker instead of home.
struct GameRoute: Hashable {
    let category: TriviaCategory
    let difficulty: TriviaDifficulty
}

@MainActor
final class PlayRouter: ObservableObject {
    @Published var path = NavigationPath()

    func popToRoot() { path = NavigationPath() }
}

struct RootView: View {
    @EnvironmentObject private var gameCenter: GameCenterManager
    @StateObject private var playRouter = PlayRouter()

    var body: some View {
        TabView {
            NavigationStack(path: $playRouter.path) { HomeView() }
                .environmentObject(playRouter)
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
                DailyChallengeCard()
                Text("Or choose a category to play")
                    .font(.title2.bold())
                    .accessibilityAddTraits(.isHeader)
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(TriviaCategory.allCases) { category in
                        NavigationLink(value: category) { CategoryCard(category: category) }
                            .buttonStyle(.plain)
                    }
                }
            }
            .padding()
        }
        .background(AppTheme.background)
        // Pinned above the tab bar rather than left at the end of the
        // scrolling content, where it sat below the whole category grid and
        // was essentially never on screen. Living here rather than on the
        // TabView itself means it also disappears automatically the moment a
        // round is pushed: GameView hides the tab bar for the same reason a
        // round should be distraction-free, and once HomeView is off the top
        // of the NavigationStack this inset isn't rendered either.
        .safeAreaInset(edge: .bottom, spacing: 0) { AdBannerView() }
        .navigationDestination(for: TriviaCategory.self) { DifficultyView(category: $0) }
        .navigationDestination(for: GameRoute.self) { GameView(category: $0.category, difficulty: $0.difficulty) }
        .navigationDestination(for: DailyRoute.self) { _ in DailyChallengeView() }
        .navigationTitle("EZ Trivia")
        .navigationBarTitleDisplayMode(.inline)
    }

    /// Plain type rather than a filled card.
    ///
    /// This was a gradient-filled rounded rectangle sitting directly above
    /// the Daily Challenge card, which is a gradient-filled rounded rectangle
    /// that *is* tappable. Two identical-looking blocks where only one
    /// responds to a tap is a straightforward usability bug, so the gradient
    /// now means exactly one thing on this screen: you can tap this.
    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Ready to play?")
                .font(.largeTitle.bold())
                .accessibilityAddTraits(.isHeader)
            Text("Ten quick questions. One great score.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
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
                NavigationLink(value: GameRoute(category: category, difficulty: difficulty)) {
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

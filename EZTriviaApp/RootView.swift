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

/// Quick Play is its own route because it is a mixed-category round rather
/// than a category/difficulty pair.
struct QuickPlayRoute: Hashable {}

private enum AppTab: Hashable {
    case play, scores, settings
}

@MainActor
final class PlayRouter: ObservableObject {
    @Published var path = NavigationPath()

    func popToRoot() { path = NavigationPath() }

    func openFriendChallenge(_ code: FriendChallengeCode) {
        path = NavigationPath()
        path.append(FriendChallengeRoute.play(seed: code.seed, invitation: code))
    }
}

struct RootView: View {
    @EnvironmentObject private var gameCenter: GameCenterManager
    @EnvironmentObject private var scores: ScoreStore
    @StateObject private var playRouter = PlayRouter()
    @State private var selectedTab = AppTab.play

    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack(path: $playRouter.path) { HomeView() }
                .environmentObject(playRouter)
                .tabItem { Label("Play", systemImage: "play.fill") }
                .tag(AppTab.play)
            NavigationStack { LeaderboardView() }
                .tabItem { Label("Scores", systemImage: "trophy.fill") }
                .tag(AppTab.scores)
            NavigationStack { SettingsView() }
                .tabItem { Label("Settings", systemImage: "gearshape.fill") }
                .tag(AppTab.settings)
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
        .onOpenURL { url in
            guard let code = FriendChallengeLink.code(from: url) else { return }
            selectedTab = .play
            playRouter.openFriendChallenge(code)
        }
        .task(id: gameCenter.isAuthenticated) {
            guard gameCenter.isAuthenticated else { return }
            await gameCenter.refreshAchievements(
                localProgress: AchievementCatalog.progress(using: scores)
            )
        }
    }
}

struct HomeView: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private var columns: [GridItem] {
        if dynamicTypeSize.isAccessibilitySize {
            return [GridItem(.flexible())]
        }
        return [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                header
                QuickPlayCard()
                DailyChallengeCard()
                FriendChallengeCard()
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
        .navigationDestination(for: QuickPlayRoute.self) { _ in QuickPlayView() }
        .navigationDestination(for: DailyRoute.self) { _ in DailyChallengeView() }
        .navigationDestination(for: FriendChallengeRoute.self) { route in
            switch route {
            case .lobby:
                FriendChallengeLobbyView()
            case let .play(seed, invitation):
                FriendChallengeGameView(seed: seed, invitation: invitation)
            }
        }
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

private struct QuickPlayCard: View {
    var body: some View {
        NavigationLink(value: QuickPlayRoute()) {
            HStack(spacing: 16) {
                Image(systemName: "shuffle")
                    .font(.system(size: 25, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 62, height: 62)
                    .background(.white.opacity(0.18), in: RoundedRectangle(cornerRadius: 16))

                VStack(alignment: .leading, spacing: 3) {
                    Text("Quick Play").font(.headline)
                    Text("Ten categories. Easy to hard.")
                        .font(.subheadline)
                        .opacity(0.9)
                }
                .foregroundStyle(.white)

                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.body.bold())
                    .foregroundStyle(.white.opacity(0.8))
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                LinearGradient(
                    colors: [.purple, .indigo],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                ),
                in: RoundedRectangle(cornerRadius: 22, style: .continuous)
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Quick Play. Ten mixed categories from Easy to Hard.")
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
            Text(category.title)
                .font(.headline)
                .foregroundStyle(.primary)
                .lineLimit(1)
                .minimumScaleFactor(0.78)
            Text(category.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            HStack(spacing: 4) {
                ForEach(TriviaDifficulty.allCases) { difficulty in
                    VStack(spacing: 2) {
                        Image(systemName: difficulty.symbol)
                        Text(scores.bestScore(for: category, difficulty: difficulty).map { "\($0)%" } ?? "—")
                    }
                    .frame(maxWidth: .infinity)
                    .lineLimit(1)
                    .minimumScaleFactor(0.72)
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

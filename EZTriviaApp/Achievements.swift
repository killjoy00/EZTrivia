import SwiftUI

struct AchievementDefinition: Identifiable, Hashable {
    enum Goal: Hashable {
        case rounds(Int)
        case quickRounds(Int)
        case friendChallenges(Int)
        case perfect(TriviaDifficulty)
        case perfectDaily
        case categories(Int)
        case streak(Int)
        case lifetimePoints(Int)
    }

    let id: String
    let title: String
    let preEarnedDescription: String
    let earnedDescription: String
    /// Existing Game Center achievements already consume Apple's full 1,000
    /// point budget. New achievements can still be meaningful in-app badges;
    /// `gameCenterPoints == nil` keeps those out of the Game Center total and
    /// labels them clearly in the native achievement list.
    let gameCenterPoints: Int?
    let symbol: String
    let goal: Goal

    var points: Int { gameCenterPoints ?? 0 }
    var isGameCenterAchievement: Bool { gameCenterPoints != nil }
}

@MainActor
enum AchievementCatalog {
    static let all: [AchievementDefinition] = [
        AchievementDefinition(
            id: "EZTrivia.achievement.first_round",
            title: "First Round",
            preEarnedDescription: "Complete any round.",
            earnedDescription: "You completed your first round.",
            gameCenterPoints: 25,
            symbol: "play.fill",
            goal: .rounds(1)
        ),
        AchievementDefinition(
            id: "EZTrivia.achievement.perfect_easy",
            title: "Easy Does It",
            preEarnedDescription: "Earn a perfect score on an Easy category round.",
            earnedDescription: "You earned a perfect Easy score.",
            gameCenterPoints: 50,
            symbol: "star",
            goal: .perfect(.easy)
        ),
        AchievementDefinition(
            id: "EZTrivia.achievement.perfect_medium",
            title: "Perfectly Balanced",
            preEarnedDescription: "Earn a perfect score on a Medium category round.",
            earnedDescription: "You earned a perfect Medium score.",
            gameCenterPoints: 75,
            symbol: "star.leadinghalf.filled",
            goal: .perfect(.medium)
        ),
        AchievementDefinition(
            id: "EZTrivia.achievement.perfect_hard",
            title: "Hard to Beat",
            preEarnedDescription: "Earn a perfect score on a Hard category round.",
            earnedDescription: "You earned a perfect Hard score.",
            gameCenterPoints: 100,
            symbol: "star.fill",
            goal: .perfect(.hard)
        ),
        AchievementDefinition(
            id: "EZTrivia.achievement.all_categories",
            title: "A Little of Everything",
            preEarnedDescription: "Play twelve different categories.",
            earnedDescription: "You played twelve different categories.",
            gameCenterPoints: 100,
            symbol: "square.grid.3x3.fill",
            goal: .categories(12)
        ),
        AchievementDefinition(
            id: "EZTrivia.achievement.all_categories_14",
            title: "Full Spectrum",
            preEarnedDescription: "Play fourteen different categories.",
            earnedDescription: "You played fourteen different categories.",
            gameCenterPoints: 100,
            symbol: "sparkles",
            goal: .categories(14)
        ),
        AchievementDefinition(
            id: "EZTrivia.achievement.streak_7",
            title: "One-Week Streak",
            preEarnedDescription: "Complete the Daily Challenge seven days in a row.",
            earnedDescription: "You completed a seven-day Daily Challenge streak.",
            gameCenterPoints: 75,
            symbol: "flame.fill",
            goal: .streak(7)
        ),
        AchievementDefinition(
            id: "EZTrivia.achievement.streak_30",
            title: "Monthly Ritual",
            preEarnedDescription: "Complete the Daily Challenge thirty days in a row.",
            earnedDescription: "You completed a thirty-day Daily Challenge streak.",
            gameCenterPoints: 100,
            symbol: "calendar",
            goal: .streak(30)
        ),
        AchievementDefinition(
            id: "EZTrivia.achievement.rounds_10",
            title: "Getting Warmed Up",
            preEarnedDescription: "Complete ten rounds.",
            earnedDescription: "You completed ten rounds.",
            gameCenterPoints: 50,
            symbol: "10.circle.fill",
            goal: .rounds(10)
        ),
        AchievementDefinition(
            id: "EZTrivia.achievement.rounds_50",
            title: "Trivia Regular",
            preEarnedDescription: "Complete fifty rounds.",
            earnedDescription: "You completed fifty rounds.",
            gameCenterPoints: 75,
            symbol: "50.circle.fill",
            goal: .rounds(50)
        ),
        AchievementDefinition(
            id: "EZTrivia.achievement.rounds_100",
            title: "Century Club",
            preEarnedDescription: "Complete one hundred rounds.",
            earnedDescription: "You completed one hundred rounds.",
            gameCenterPoints: 100,
            symbol: "medal.fill",
            goal: .rounds(100)
        ),
        AchievementDefinition(
            id: "EZTrivia.achievement.points_10000",
            title: "Five Figures",
            preEarnedDescription: "Earn 10,000 lifetime category points.",
            earnedDescription: "You earned 10,000 lifetime category points.",
            gameCenterPoints: 75,
            symbol: "chart.line.uptrend.xyaxis",
            goal: .lifetimePoints(10_000)
        ),
        AchievementDefinition(
            id: "EZTrivia.achievement.points_50000",
            title: "Point Collector",
            preEarnedDescription: "Earn 50,000 lifetime category points.",
            earnedDescription: "You earned 50,000 lifetime category points.",
            gameCenterPoints: 75,
            symbol: "trophy.fill",
            goal: .lifetimePoints(50_000)
        ),

        // New in-app achievements. Apple's Game Center budget is already fully
        // allocated by the thirteen achievements above, so these sync through
        // EZ Trivia's iCloud player state instead of claiming additional Game
        // Center points.
        AchievementDefinition(
            id: "EZTrivia.local.all_categories_16",
            title: "The Whole Board",
            preEarnedDescription: "Play all sixteen trivia categories.",
            earnedDescription: "You played every category in EZ Trivia.",
            gameCenterPoints: nil,
            symbol: "square.grid.4x3.fill",
            goal: .categories(16)
        ),
        AchievementDefinition(
            id: "EZTrivia.local.quick_play_1",
            title: "Shuffle Up",
            preEarnedDescription: "Complete your first Quick Play round.",
            earnedDescription: "You completed your first Quick Play round.",
            gameCenterPoints: nil,
            symbol: "shuffle",
            goal: .quickRounds(1)
        ),
        AchievementDefinition(
            id: "EZTrivia.local.quick_play_10",
            title: "Mix Master",
            preEarnedDescription: "Complete ten Quick Play rounds.",
            earnedDescription: "You completed ten Quick Play rounds.",
            gameCenterPoints: nil,
            symbol: "shuffle.circle.fill",
            goal: .quickRounds(10)
        ),
        AchievementDefinition(
            id: "EZTrivia.local.friend_challenges_5",
            title: "Friendly Rivalry",
            preEarnedDescription: "Complete five Friend Challenges.",
            earnedDescription: "You completed five Friend Challenges.",
            gameCenterPoints: nil,
            symbol: "person.2.fill",
            goal: .friendChallenges(5)
        ),
        AchievementDefinition(
            id: "EZTrivia.local.daily_perfect",
            title: "Daily Ace",
            preEarnedDescription: "Score 10/10 on a Daily Challenge.",
            earnedDescription: "You earned a perfect Daily Challenge score.",
            gameCenterPoints: nil,
            symbol: "calendar.badge.checkmark",
            goal: .perfectDaily
        ),
        AchievementDefinition(
            id: "EZTrivia.local.streak_100",
            title: "Hundred-Day Habit",
            preEarnedDescription: "Complete the Daily Challenge one hundred days in a row.",
            earnedDescription: "You reached a one-hundred-day Daily Challenge streak.",
            gameCenterPoints: nil,
            symbol: "flame.circle.fill",
            goal: .streak(100)
        )
    ]

    static var gameCenterAchievements: [AchievementDefinition] {
        all.filter(\.isGameCenterAchievement)
    }

    static var totalGameCenterPoints: Int {
        gameCenterAchievements.reduce(0) { $0 + $1.points }
    }

    static func progress(using scores: ScoreStore) -> [String: Double] {
        Dictionary(uniqueKeysWithValues: all.map { definition in
            (definition.id, progress(for: definition, using: scores))
        })
    }

    private static func progress(
        for definition: AchievementDefinition,
        using scores: ScoreStore
    ) -> Double {
        switch definition.goal {
        case let .rounds(target):
            percentage(scores.totalRoundsCompleted, target: target)
        case let .quickRounds(target):
            percentage(scores.quickPlayRoundsCompleted, target: target)
        case let .friendChallenges(target):
            percentage(scores.friendChallengesCompleted, target: target)
        case let .perfect(difficulty):
            scores.perfectDifficulties.contains(difficulty) ? 100 : 0
        case .perfectDaily:
            scores.hasPerfectDaily ? 100 : 0
        case let .categories(target):
            percentage(scores.playedCategories.count, target: target)
        case let .streak(target):
            percentage(scores.longestDailyStreak, target: target)
        case let .lifetimePoints(target):
            percentage(scores.lifetimePointsTotal, target: target)
        }
    }

    private static func percentage(_ value: Int, target: Int) -> Double {
        guard target > 0 else { return 100 }
        return min(Double(value) / Double(target) * 100, 100)
    }
}

struct AchievementsView: View {
    @EnvironmentObject private var scores: ScoreStore
    @EnvironmentObject private var gameCenter: GameCenterManager

    private var localProgress: [String: Double] {
        AchievementCatalog.progress(using: scores)
    }

    var body: some View {
        // Built once per render and passed down. As a computed property read
        // from every row this rebuilt the whole dictionary a couple of dozen
        // times per pass, and each rebuild walks the full daily history to
        // recompute the longest streak.
        let progress = resolvedProgress
        let completedCount = progress.values.count { $0 >= 100 }

        return List {
            Section {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("\(completedCount) of \(AchievementCatalog.all.count) unlocked")
                            .font(.headline)
                        Spacer()
                        if gameCenter.isSyncingAchievements { ProgressView() }
                    }
                    ProgressView(value: Double(completedCount), total: Double(AchievementCatalog.all.count))
                        .tint(.indigo)
                    Text("\(earnedGameCenterPoints(from: progress)) of \(AchievementCatalog.totalGameCenterPoints) Game Center points")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(.vertical, 4)
            }

            Section {
                ForEach(AchievementCatalog.all) { achievement in
                    achievementRow(achievement, progress: progress[achievement.id] ?? 0)
                }
            } footer: {
                Text("The original thirteen achievements sync with Game Center. New Quick Play, Friend Challenge, all-category, and extended Daily badges are saved with your iCloud player state because the Game Center achievement point budget is already fully allocated.")
            }
        }
        .navigationTitle("Achievements")
        .task {
            await gameCenter.refreshAchievements(localProgress: localProgress)
        }
        .refreshable {
            await gameCenter.refreshAchievements(localProgress: localProgress)
        }
    }

    private func achievementRow(_ achievement: AchievementDefinition, progress: Double) -> some View {
        let complete = progress >= 100
        return HStack(alignment: .top, spacing: 14) {
            Image(systemName: achievement.symbol)
                .font(.title3.bold())
                .foregroundStyle(complete ? .white : .indigo)
                .frame(width: 42, height: 42)
                .background(complete ? Color.indigo : Color.indigo.opacity(0.12), in: Circle())

            VStack(alignment: .leading, spacing: 5) {
                HStack(alignment: .firstTextBaseline) {
                    Text(achievement.title).font(.headline)
                    Spacer()
                    if let points = achievement.gameCenterPoints {
                        Text("\(points) pts")
                            .font(.caption.bold())
                            .foregroundStyle(.secondary)
                    } else {
                        Text("EZ badge")
                            .font(.caption.bold())
                            .foregroundStyle(.secondary)
                    }
                }
                Text(complete ? achievement.earnedDescription : achievement.preEarnedDescription)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                ProgressView(value: progress, total: 100)
                    .tint(complete ? .green : .indigo)
                Text(complete ? "Unlocked" : "\(Int(progress.rounded(.down)))%")
                    .font(.caption2.bold())
                    .foregroundStyle(complete ? .green : .secondary)
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }

    /// Local and Game Center progress merged per achievement, taking whichever
    /// is further along so neither a reinstall nor an offline session can move
    /// a badge backward. In-app-only badges have no Game Center counterpart,
    /// so their resolved value is simply local/iCloud progress.
    private var resolvedProgress: [String: Double] {
        let local = localProgress
        return Dictionary(uniqueKeysWithValues: AchievementCatalog.all.map { achievement in
            let remote = achievement.isGameCenterAchievement
                ? gameCenter.achievementProgressByID[achievement.id] ?? 0
                : 0
            return (achievement.id, max(local[achievement.id] ?? 0, remote))
        })
    }

    private func earnedGameCenterPoints(from progress: [String: Double]) -> Int {
        AchievementCatalog.gameCenterAchievements
            .filter { (progress[$0.id] ?? 0) >= 100 }
            .reduce(0) { $0 + $1.points }
    }
}

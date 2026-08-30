import SwiftUI

struct AchievementDefinition: Identifiable, Hashable {
    enum Goal: Hashable {
        case rounds(Int)
        case perfect(TriviaDifficulty)
        case categories(Int)
        case streak(Int)
        case lifetimePoints(Int)
    }

    let id: String
    let title: String
    let preEarnedDescription: String
    let earnedDescription: String
    let points: Int
    let symbol: String
    let goal: Goal
}

@MainActor
enum AchievementCatalog {
    static let all: [AchievementDefinition] = [
        AchievementDefinition(
            id: "EZTrivia.achievement.first_round",
            title: "First Round",
            preEarnedDescription: "Complete any round.",
            earnedDescription: "You completed your first round.",
            points: 25,
            symbol: "play.fill",
            goal: .rounds(1)
        ),
        AchievementDefinition(
            id: "EZTrivia.achievement.perfect_easy",
            title: "Easy Does It",
            preEarnedDescription: "Earn a perfect score on an Easy category round.",
            earnedDescription: "You earned a perfect Easy score.",
            points: 50,
            symbol: "star",
            goal: .perfect(.easy)
        ),
        AchievementDefinition(
            id: "EZTrivia.achievement.perfect_medium",
            title: "Perfectly Balanced",
            preEarnedDescription: "Earn a perfect score on a Medium category round.",
            earnedDescription: "You earned a perfect Medium score.",
            points: 75,
            symbol: "star.leadinghalf.filled",
            goal: .perfect(.medium)
        ),
        AchievementDefinition(
            id: "EZTrivia.achievement.perfect_hard",
            title: "Hard to Beat",
            preEarnedDescription: "Earn a perfect score on a Hard category round.",
            earnedDescription: "You earned a perfect Hard score.",
            points: 100,
            symbol: "star.fill",
            goal: .perfect(.hard)
        ),
        AchievementDefinition(
            id: "EZTrivia.achievement.all_categories",
            title: "A Little of Everything",
            preEarnedDescription: "Complete questions from all twelve categories.",
            earnedDescription: "You played all twelve categories.",
            points: 100,
            symbol: "square.grid.3x3.fill",
            goal: .categories(TriviaCategory.allCases.count)
        ),
        AchievementDefinition(
            id: "EZTrivia.achievement.streak_7",
            title: "One-Week Streak",
            preEarnedDescription: "Complete the Daily Challenge seven days in a row.",
            earnedDescription: "You completed a seven-day Daily Challenge streak.",
            points: 75,
            symbol: "flame.fill",
            goal: .streak(7)
        ),
        AchievementDefinition(
            id: "EZTrivia.achievement.streak_30",
            title: "Monthly Ritual",
            preEarnedDescription: "Complete the Daily Challenge thirty days in a row.",
            earnedDescription: "You completed a thirty-day Daily Challenge streak.",
            points: 100,
            symbol: "calendar",
            goal: .streak(30)
        ),
        AchievementDefinition(
            id: "EZTrivia.achievement.rounds_10",
            title: "Getting Warmed Up",
            preEarnedDescription: "Complete ten rounds.",
            earnedDescription: "You completed ten rounds.",
            points: 50,
            symbol: "10.circle.fill",
            goal: .rounds(10)
        ),
        AchievementDefinition(
            id: "EZTrivia.achievement.rounds_50",
            title: "Trivia Regular",
            preEarnedDescription: "Complete fifty rounds.",
            earnedDescription: "You completed fifty rounds.",
            points: 75,
            symbol: "50.circle.fill",
            goal: .rounds(50)
        ),
        AchievementDefinition(
            id: "EZTrivia.achievement.rounds_100",
            title: "Century Club",
            preEarnedDescription: "Complete one hundred rounds.",
            earnedDescription: "You completed one hundred rounds.",
            points: 100,
            symbol: "medal.fill",
            goal: .rounds(100)
        ),
        AchievementDefinition(
            id: "EZTrivia.achievement.points_10000",
            title: "Five Figures",
            preEarnedDescription: "Earn 10,000 lifetime category points.",
            earnedDescription: "You earned 10,000 lifetime category points.",
            points: 75,
            symbol: "chart.line.uptrend.xyaxis",
            goal: .lifetimePoints(10_000)
        ),
        AchievementDefinition(
            id: "EZTrivia.achievement.points_50000",
            title: "Point Collector",
            preEarnedDescription: "Earn 50,000 lifetime category points.",
            earnedDescription: "You earned 50,000 lifetime category points.",
            points: 75,
            symbol: "trophy.fill",
            goal: .lifetimePoints(50_000)
        )
    ]

    static var totalGameCenterPoints: Int { all.reduce(0) { $0 + $1.points } }

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
        case let .perfect(difficulty):
            scores.perfectDifficulties.contains(difficulty) ? 100 : 0
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

    private var completedCount: Int {
        AchievementCatalog.all.count { combinedProgress(for: $0) >= 100 }
    }

    var body: some View {
        List {
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
                    Text("\(earnedGameCenterPoints) of \(AchievementCatalog.totalGameCenterPoints) Game Center points")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(.vertical, 4)
            }

            Section {
                ForEach(AchievementCatalog.all) { achievement in
                    achievementRow(achievement)
                }
            } footer: {
                if !gameCenter.isAuthenticated {
                    Text("Progress is saved on this device. Sign in to Game Center to sync achievements.")
                } else if gameCenter.availableAchievementIDs.count < AchievementCatalog.all.count {
                    Text("Local progress is active. Game Center sync completes after the achievement definitions are available for this app.")
                } else {
                    Text("Achievement progress is synchronized with Game Center.")
                }
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

    private func achievementRow(_ achievement: AchievementDefinition) -> some View {
        let progress = combinedProgress(for: achievement)
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
                    Text("\(achievement.points) pts")
                        .font(.caption.bold())
                        .foregroundStyle(.secondary)
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

    private func combinedProgress(for achievement: AchievementDefinition) -> Double {
        max(
            localProgress[achievement.id] ?? 0,
            gameCenter.achievementProgressByID[achievement.id] ?? 0
        )
    }

    private var earnedGameCenterPoints: Int {
        AchievementCatalog.all
            .filter { combinedProgress(for: $0) >= 100 }
            .reduce(0) { $0 + $1.points }
    }
}

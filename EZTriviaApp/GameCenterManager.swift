import GameKit
import SwiftUI

@MainActor
final class GameCenterManager: ObservableObject {
    static let leaderboardPrefix = "EZTrivia"

    @Published private(set) var isAuthenticated = GKLocalPlayer.local.isAuthenticated
    @Published var authenticationViewController: UIViewController?
    @Published var errorMessage: String?
    @Published private(set) var achievementProgressByID: [String: Double] = [:]
    @Published private(set) var availableAchievementIDs: Set<String> = []
    @Published private(set) var isSyncingAchievements = false
    private var didLoadAchievementMetadata = false

    func authenticate() {
        GKLocalPlayer.local.authenticateHandler = { [weak self] viewController, error in
            Task { @MainActor in
                self?.authenticationViewController = viewController
                self?.isAuthenticated = GKLocalPlayer.local.isAuthenticated
                if let error { self?.errorMessage = error.localizedDescription }
            }
        }
    }

    /// Submits a category's lifetime points total.
    ///
    /// The total rather than the round, and points rather than a percentage.
    /// A percentage saturates on a ten-question round, so the board fills with
    /// identical scores and stops ranking anyone; a lifetime total only ever
    /// grows and keeps separating players indefinitely.
    ///
    /// BEST_SCORE suits this exactly. Because the value is monotonic, the best
    /// score Game Center has ever seen is the current total, so a submission
    /// that arrives late or out of order can never lower a player's standing.
    func submit(lifetimePoints: Int, category: TriviaCategory) {
        guard isAuthenticated, lifetimePoints > 0 else { return }
        GKLeaderboard.submitScore(
            lifetimePoints,
            context: 0,
            player: GKLocalPlayer.local,
            leaderboardIDs: [Self.leaderboardID(for: category)]
        ) { [weak self] error in
            guard let error else { return }
            Task { @MainActor in self?.errorMessage = error.localizedDescription }
        }
    }

    /// Submits a daily challenge score.
    ///
    /// Points rather than a percentage, and a leaderboard that resets every
    /// day, so the board keeps ranking people instead of filling up with
    /// identical perfect scores.
    func submitDaily(points: Int) {
        guard isAuthenticated, points > 0 else { return }
        GKLeaderboard.submitScore(
            points,
            context: 0,
            player: GKLocalPlayer.local,
            leaderboardIDs: [Self.dailyLeaderboardID]
        ) { [weak self] error in
            guard let error else { return }
            Task { @MainActor in self?.errorMessage = error.localizedDescription }
        }
    }

    static func leaderboardID(for category: TriviaCategory) -> String {
        "\(leaderboardPrefix).\(category.rawValue)"
    }

    static let dailyLeaderboardID = "\(leaderboardPrefix).daily"

    // MARK: - Native leaderboard data

    func loadLeaderboard(
        _ board: GameCenterBoard,
        scope: GameCenterPlayerScope
    ) async throws -> GameCenterLeaderboardSnapshot {
        guard isAuthenticated else { throw GameCenterDataError.notAuthenticated }

        let leaderboard: GKLeaderboard = try await withCheckedThrowingContinuation { continuation in
            GKLeaderboard.loadLeaderboards(IDs: [board.id]) { leaderboards, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if let leaderboard = leaderboards?.first {
                    continuation.resume(returning: leaderboard)
                } else {
                    continuation.resume(throwing: GameCenterDataError.leaderboardUnavailable)
                }
            }
        }

        let loaded: (GKLeaderboard.Entry?, [GKLeaderboard.Entry], Int) = try await withCheckedThrowingContinuation { continuation in
            leaderboard.loadEntries(
                for: scope.gameKitValue,
                timeScope: board.timeScope,
                range: NSRange(location: 1, length: 10)
            ) { localEntry, entries, totalPlayerCount, error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume(returning: (localEntry, entries ?? [], totalPlayerCount))
                }
            }
        }

        return GameCenterLeaderboardSnapshot(
            localPlayer: loaded.0.map(GameCenterLeaderboardRow.init),
            leaders: loaded.1.map(GameCenterLeaderboardRow.init),
            totalPlayerCount: loaded.2
        )
    }

    // MARK: - Achievements

    /// Loads Game Center's saved progress, then reports any higher progress
    /// established by local play. Local progress remains the source of truth
    /// for this device; taking the maximum means neither side can move a badge
    /// backward after a reinstall, delayed callback, or offline session.
    func refreshAchievements(localProgress: [String: Double]) async {
        guard isAuthenticated else { return }
        isSyncingAchievements = true
        defer { isSyncingAchievements = false }

        do {
            try await loadAchievementMetadataIfNeeded()
            let achievements: [GKAchievement] = try await withCheckedThrowingContinuation { continuation in
                GKAchievement.loadAchievements { achievements, error in
                    if let error {
                        continuation.resume(throwing: error)
                    } else {
                        continuation.resume(returning: achievements ?? [])
                    }
                }
            }
            achievementProgressByID = Dictionary(
                uniqueKeysWithValues: achievements.map { ($0.identifier, $0.percentComplete) }
            )
            try await reportHigherLocalProgress(localProgress)
        } catch {
            Telemetry.record(error)
        }
    }

    func syncAchievements(localProgress: [String: Double]) async {
        guard isAuthenticated else { return }
        isSyncingAchievements = true
        defer { isSyncingAchievements = false }
        do {
            try await loadAchievementMetadataIfNeeded()
            try await reportHigherLocalProgress(localProgress)
        } catch {
            // A missing App Store Connect component must not interrupt a quiz
            // with an alert. The native Achievements screen still shows local
            // progress, and a later successful sync will catch up.
            Telemetry.record(error)
        }
    }

    private func loadAchievementMetadataIfNeeded() async throws {
        guard !didLoadAchievementMetadata else { return }
        let descriptions: [GKAchievementDescription] = try await withCheckedThrowingContinuation { continuation in
            GKAchievementDescription.loadAchievementDescriptions { descriptions, error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume(returning: descriptions ?? [])
                }
            }
        }
        availableAchievementIDs = Set(descriptions.map(\.identifier))
        didLoadAchievementMetadata = true
    }

    private func reportHigherLocalProgress(_ localProgress: [String: Double]) async throws {
        let reportable = localProgress.compactMap { identifier, localValue -> GKAchievement? in
            guard availableAchievementIDs.contains(identifier) else { return nil }
            let clamped = min(max(localValue, 0), 100)
            guard clamped > (achievementProgressByID[identifier] ?? 0) else { return nil }
            let achievement = GKAchievement(identifier: identifier)
            achievement.percentComplete = clamped
            achievement.showsCompletionBanner = clamped >= 100
            return achievement
        }
        guard !reportable.isEmpty else { return }

        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            GKAchievement.report(reportable) { error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume(returning: ())
                }
            }
        }
        for achievement in reportable {
            achievementProgressByID[achievement.identifier] = achievement.percentComplete
        }
    }
}

enum GameCenterPlayerScope: String, CaseIterable, Identifiable {
    case global
    case friends

    var id: String { rawValue }
    var title: String { self == .global ? "Global" : "Friends" }
    var gameKitValue: GKLeaderboard.PlayerScope { self == .global ? .global : .friendsOnly }
}

struct GameCenterBoard: Identifiable, Hashable {
    let id: String
    let title: String
    let isDaily: Bool

    var timeScope: GKLeaderboard.TimeScope { isDaily ? .today : .allTime }

    static let all: [GameCenterBoard] = [
        GameCenterBoard(id: "EZTrivia.daily", title: "Daily Challenge", isDaily: true)
    ] + TriviaCategory.allCases.map {
        GameCenterBoard(
            id: "EZTrivia.\($0.rawValue)",
            title: $0.title,
            isDaily: false
        )
    }
}

struct GameCenterLeaderboardRow: Identifiable, Equatable {
    let id: String
    let rank: Int
    let playerName: String
    let score: Int
    let formattedScore: String
    let isLocalPlayer: Bool

    init(_ entry: GKLeaderboard.Entry) {
        id = "\(entry.player.gamePlayerID)-\(entry.rank)"
        rank = entry.rank
        playerName = entry.player.displayName
        score = entry.score
        formattedScore = entry.formattedScore
        isLocalPlayer = entry.player.gamePlayerID == GKLocalPlayer.local.gamePlayerID
    }
}

struct GameCenterLeaderboardSnapshot: Equatable {
    let localPlayer: GameCenterLeaderboardRow?
    let leaders: [GameCenterLeaderboardRow]
    let totalPlayerCount: Int
}

private enum GameCenterDataError: LocalizedError {
    case notAuthenticated
    case leaderboardUnavailable

    var errorDescription: String? {
        switch self {
        case .notAuthenticated: "Sign in to Game Center to view this leaderboard."
        case .leaderboardUnavailable: "This leaderboard is not available in Game Center yet."
        }
    }
}

struct GameCenterAuthenticationView: UIViewControllerRepresentable {
    let viewController: UIViewController
    let onDismiss: () -> Void

    func makeUIViewController(context: Context) -> UIViewController { viewController }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
    static func dismantleUIViewController(_ uiViewController: UIViewController, coordinator: ()) {}
}

struct GameCenterDashboard: UIViewControllerRepresentable {
    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIViewController(context: Context) -> GKGameCenterViewController {
        let controller = GKGameCenterViewController(state: .leaderboards)
        controller.gameCenterDelegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ uiViewController: GKGameCenterViewController, context: Context) {}

    final class Coordinator: NSObject, GKGameCenterControllerDelegate {
        func gameCenterViewControllerDidFinish(_ gameCenterViewController: GKGameCenterViewController) {
            gameCenterViewController.dismiss(animated: true)
        }
    }
}

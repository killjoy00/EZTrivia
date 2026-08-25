import GameKit
import SwiftUI

@MainActor
final class GameCenterManager: ObservableObject {
    static let leaderboardPrefix = "EZTrivia"

    @Published private(set) var isAuthenticated = GKLocalPlayer.local.isAuthenticated
    @Published var authenticationViewController: UIViewController?
    @Published var errorMessage: String?

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

import Foundation
import StoreKit
import SwiftUI

/// Decides when to ask a player to rate the app.
///
/// The ask itself is one line of SwiftUI. Everything here is about *when*, which
/// is the part that decides whether the rating is a good one. Three rules shape
/// it:
///
/// - Ask only after a round that went well. A prompt that lands right after
///   someone scores 3/10 is asking a frustrated person for a public rating.
/// - Ask only once the player has some history, so a first-launch visitor is
///   never interrupted by a favor request.
/// - Ask rarely. iOS silently caps this at three prompts a year, and a prompt
///   spent on a lukewarm moment is one that is not available for a better one.
///
/// The Settings screen also carries a direct "Rate EZ Trivia" link. That path is
/// deliberately separate: it opens the App Store, is always available, and costs
/// nothing from the system quota, so a player who *wants* to leave a review is
/// never gated by the rules above.
@MainActor
final class ReviewPrompt: ObservableObject {
    private enum Keys {
        static let lastPromptedVersion = "review.lastPromptedVersion"
        static let lastPromptedAt = "review.lastPromptedAt"
    }

    private let defaults: UserDefaults
    private let currentVersion: String

    init(
        defaults: UserDefaults = .standard,
        bundle: Bundle = .main
    ) {
        self.defaults = defaults
        currentVersion = bundle.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
    }

    /// Whether a finished round is a good moment to ask.
    ///
    /// Reads the stored state and hands the decision to `ReviewPromptPolicy`,
    /// which is where the rules live and where they are tested.
    func shouldRequestReview(
        score: Int,
        total: Int,
        roundsCompleted: Int,
        now: Date = Date()
    ) -> Bool {
        var daysSinceLastPrompt: Int?
        if let last = defaults.object(forKey: Keys.lastPromptedAt) as? Date {
            daysSinceLastPrompt = Calendar.current.dateComponents([.day], from: last, to: now).day ?? 0
        }

        return ReviewPromptPolicy.shouldAsk(
            .init(
                score: score,
                total: total,
                roundsCompleted: roundsCompleted,
                currentVersion: currentVersion,
                lastPromptedVersion: defaults.string(forKey: Keys.lastPromptedVersion),
                daysSinceLastPrompt: daysSinceLastPrompt
            )
        )
    }

    /// Records that the prompt was shown. Called only when the app actually
    /// asks, so a round that fails the checks above never spends the slot.
    func recordPrompted(now: Date = Date()) {
        defaults.set(currentVersion, forKey: Keys.lastPromptedVersion)
        defaults.set(now, forKey: Keys.lastPromptedAt)
    }

    /// Deep link to the App Store review sheet, for the Settings entry.
    static var writeReviewURL: URL? {
        URL(string: "\(RoundSummary.appStoreURL)?action=write-review")
    }
}

extension View {
    /// Asks for a review after a round, if this is a good moment for it.
    ///
    /// Wraps the environment action so the call site at each result screen stays
    /// one modifier, and so the "did we ask?" bookkeeping cannot be forgotten at
    /// one of them.
    func requestReviewIfEarned(
        _ prompt: ReviewPrompt,
        score: Int,
        total: Int,
        roundsCompleted: Int
    ) -> some View {
        modifier(
            ReviewPromptModifier(
                prompt: prompt,
                score: score,
                total: total,
                roundsCompleted: roundsCompleted
            )
        )
    }
}

private struct ReviewPromptModifier: ViewModifier {
    @Environment(\.requestReview) private var requestReview
    let prompt: ReviewPrompt
    let score: Int
    let total: Int
    let roundsCompleted: Int
    @State private var asked = false

    func body(content: Content) -> some View {
        content.task {
            guard !asked,
                  prompt.shouldRequestReview(
                    score: score,
                    total: total,
                    roundsCompleted: roundsCompleted
                  ) else { return }
            asked = true

            // A beat after the result lands, so the score and the celebration
            // are on screen before the system sheet covers them.
            try? await Task.sleep(nanoseconds: 1_200_000_000)
            guard !Task.isCancelled else { return }

            prompt.recordPrompted()
            requestReview()
        }
    }
}

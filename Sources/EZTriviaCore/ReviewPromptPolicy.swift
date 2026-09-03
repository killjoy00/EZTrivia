import Foundation

/// When it is reasonable to ask a player to rate the app.
///
/// Separated from the StoreKit call and the stored state so the rules are
/// testable on their own. Asking is cheap to get wrong and expensive to fix: a
/// prompt shown after a bad round invites the review that round deserves, not
/// the one the app deserves, and iOS discards prompts past three a year without
/// telling the app which ones it dropped.
public enum ReviewPromptPolicy {
    /// Rounds a player must finish before the app asks for anything.
    public static let minimumRounds = 5

    /// How well the round in hand has to have gone, as a fraction correct.
    public static let minimumScoreRatio = 0.7

    /// Our own spacing, well inside Apple's three-per-year cap.
    public static let minimumDaysBetweenPrompts = 120

    /// Everything the decision depends on, gathered so the rule itself stays a
    /// pure function of its inputs.
    public struct Context: Sendable {
        public let score: Int
        public let total: Int
        public let roundsCompleted: Int
        public let currentVersion: String
        public let lastPromptedVersion: String?
        public let daysSinceLastPrompt: Int?

        public init(
            score: Int,
            total: Int,
            roundsCompleted: Int,
            currentVersion: String,
            lastPromptedVersion: String?,
            daysSinceLastPrompt: Int?
        ) {
            self.score = score
            self.total = total
            self.roundsCompleted = roundsCompleted
            self.currentVersion = currentVersion
            self.lastPromptedVersion = lastPromptedVersion
            self.daysSinceLastPrompt = daysSinceLastPrompt
        }
    }

    public static func shouldAsk(_ context: Context) -> Bool {
        guard context.total > 0,
              context.roundsCompleted >= minimumRounds,
              Double(context.score) / Double(context.total) >= minimumScoreRatio
        else { return false }

        // One ask per released version at most. A player who dismissed the
        // prompt has answered it; asking again before anything has changed is
        // the same question a second time.
        if context.lastPromptedVersion == context.currentVersion { return false }

        if let days = context.daysSinceLastPrompt, days < minimumDaysBetweenPrompts {
            return false
        }
        return true
    }
}

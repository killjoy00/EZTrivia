import Foundation

/// Builds the text a player sends someone after finishing a round.
///
/// Kept out of the views and in the core so it can be tested, because share
/// text is the one piece of the app that gets seen by people who have not
/// installed it. A wrong grid or a broken line break is visible to an audience
/// the app never otherwise reaches.
public enum RoundSummary {
    private static let correctMark = "🟩"
    private static let wrongMark = "⬜️"

    /// The result grid: one square per question, in the order they were asked.
    public static func grid(_ outcomes: [Bool]) -> String {
        outcomes.map { $0 ? correctMark : wrongMark }.joined()
    }

    /// Share text for a daily challenge.
    ///
    /// Deliberately does not name the questions or the answers. Someone who has
    /// not played today should be able to read this without it spoiling their
    /// round, which is the whole reason the grid is squares rather than words.
    public static func daily(
        day: Int,
        outcomes: [Bool],
        points: Int,
        streak: Int
    ) -> String {
        var lines = [
            "EZ Trivia Daily #\(day) — \(outcomes.filter { $0 }.count)/\(outcomes.count)",
            grid(outcomes),
            "\(points.formatted()) points"
        ]
        if streak > 1 {
            lines.append("\(streak) day streak 🔥")
        }
        return lines.joined(separator: "\n")
    }

    /// Share text for an ordinary category round.
    public static func round(
        category: TriviaCategory,
        difficulty: TriviaDifficulty,
        outcomes: [Bool]
    ) -> String {
        [
            "EZ Trivia — \(category.title), \(difficulty.title)",
            grid(outcomes),
            "\(outcomes.filter { $0 }.count)/\(outcomes.count) correct"
        ].joined(separator: "\n")
    }
}

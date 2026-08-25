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

    /// Where a shared score sends someone who does not have the app.
    ///
    /// The numeric App Store id is stable from the moment the app record is
    /// created, so this link is correct in advance of release -- it just
    /// 404s until the listing goes live. Shipping it now means launch day
    /// needs no code change and no forgotten follow-up.
    public static let appStoreURL = "https://apps.apple.com/app/id6804526553"

    /// A one-line headline for the shared link preview.
    ///
    /// This is the text that sits under the score card in a Messages bubble, so
    /// it has to stand alone: the grid is in the image, not here.
    public static func headline(correct: Int, total: Int) -> String {
        "I scored \(correct)/\(total) on EZ Trivia"
    }

    /// The result grid: one square per question, in the order they were asked.
    public static func grid(_ outcomes: [Bool]) -> String {
        outcomes.map { $0 ? correctMark : wrongMark }.joined()
    }

    /// Share text for a daily challenge.
    ///
    /// Deliberately does not name the questions or the answers. Someone who has
    /// not played today should be able to read this without it spoiling their
    /// round, which is the whole reason the grid is squares rather than words.
    /// `includingLink` is false when the share itself carries the App Store URL
    /// as its item -- the score card is the tappable link in that case, so
    /// repeating the address in the text would send the same URL twice.
    public static func daily(
        day: Int,
        outcomes: [Bool],
        points: Int,
        streak: Int,
        includingLink: Bool = true
    ) -> String {
        var lines = [
            "EZ Trivia Daily #\(day) — \(outcomes.filter { $0 }.count)/\(outcomes.count)",
            grid(outcomes),
            "\(points.formatted()) points"
        ]
        if streak > 1 {
            lines.append("\(streak) day streak 🔥")
        }
        if includingLink {
            lines.append(appStoreURL)
        }
        return lines.joined(separator: "\n")
    }

    /// Share text for an ordinary category round.
    public static func round(
        category: TriviaCategory,
        difficulty: TriviaDifficulty,
        outcomes: [Bool],
        includingLink: Bool = true
    ) -> String {
        var lines = [
            "EZ Trivia — \(category.title), \(difficulty.title)",
            grid(outcomes),
            "\(outcomes.filter { $0 }.count)/\(outcomes.count) correct"
        ]
        if includingLink {
            lines.append(appStoreURL)
        }
        return lines.joined(separator: "\n")
    }
}

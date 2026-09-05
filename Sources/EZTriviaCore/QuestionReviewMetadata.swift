import Foundation

/// Editorial provenance for facts that can change after the app ships.
///
/// Stable literary, scientific, and historical facts do not need a scheduled
/// recheck. League rules, active records, population rankings, and current
/// award totals do. Keeping those dates beside the bank lets CI turn stale
/// trivia into a visible maintenance task instead of a player correction.
public struct QuestionReviewMetadata: Sendable, Equatable {
    public let sourceURL: String
    public let verifiedOn: String
    public let reviewAfter: String

    public init(sourceURL: String, verifiedOn: String, reviewAfter: String) {
        self.sourceURL = sourceURL
        self.verifiedOn = verifiedOn
        self.reviewAfter = reviewAfter
    }
}

public enum QuestionReviewRegistry {
    private static let nbaRules = "https://official.nba.com/rulebook/"
    private static let nbaPlayers = "https://official.nba.com/rule-no-3-players-substitutes-and-coaches/"
    private static let nbaViolations = "https://official.nba.com/rule-no-10-violations-and-penalties/"
    private static let nbaGoaltending = "https://official.nba.com/rule-no-11-basket-interference-goaltending/"
    private static let nbaCBA = "https://nbpa.com/cba/"
    private static let nflRules = "https://static.www.nfl.com/image/upload/fl_attachment/league/tqivdkzt9mu6wdgsh1ku.pdf"
    private static let ifabPlayers = "https://www.theifab.com/laws/latest/the-players/"
    private static let ifabDuration = "https://www.theifab.com/laws/latest/the-duration-of-the-match/"
    private static let ifabFouls = "https://www.theifab.com/laws/latest/fouls-and-misconduct/"
    private static let ifabGoalKick = "https://www.theifab.com/laws/latest/the-goal-kick/"

    public static let byQuestionID: [String: QuestionReviewMetadata] = [
        "basketball-easy-45": .init(sourceURL: nbaRules, verifiedOn: "2026-09-04", reviewAfter: "2027-07-01"),
        "basketball-medium-8": .init(sourceURL: nbaRules, verifiedOn: "2026-08-31", reviewAfter: "2027-07-01"),
        "basketball-medium-7": .init(sourceURL: nbaGoaltending, verifiedOn: "2026-08-31", reviewAfter: "2027-07-01"),
        "basketball-medium-14": .init(sourceURL: nbaPlayers, verifiedOn: "2026-08-31", reviewAfter: "2027-07-01"),
        "basketball-hard-19": .init(sourceURL: nbaViolations, verifiedOn: "2026-08-31", reviewAfter: "2027-07-01"),
        "basketball-hard-25": .init(sourceURL: nbaCBA, verifiedOn: "2026-08-31", reviewAfter: "2027-07-01"),
        "football-medium-5": .init(sourceURL: nflRules, verifiedOn: "2026-08-31", reviewAfter: "2027-06-01"),
        "football-medium-18": .init(sourceURL: nflRules, verifiedOn: "2026-08-31", reviewAfter: "2027-06-01"),
        "football-hard-11": .init(sourceURL: nflRules, verifiedOn: "2026-08-31", reviewAfter: "2027-06-01"),
        "football-hard-26": .init(sourceURL: nflRules, verifiedOn: "2026-08-31", reviewAfter: "2027-06-01"),
        "football-hard-20": .init(sourceURL: nflRules, verifiedOn: "2026-08-31", reviewAfter: "2027-06-01"),
        "soccer-easy-12": .init(sourceURL: ifabFouls, verifiedOn: "2026-08-31", reviewAfter: "2027-07-01"),
        "soccer-easy-49": .init(sourceURL: ifabFouls, verifiedOn: "2026-09-04", reviewAfter: "2027-07-01"),
        "soccer-easy-20": .init(sourceURL: ifabPlayers, verifiedOn: "2026-08-31", reviewAfter: "2027-07-01"),
        "soccer-medium-21": .init(sourceURL: ifabDuration, verifiedOn: "2026-08-31", reviewAfter: "2027-07-01"),
        "soccer-medium-13": .init(sourceURL: ifabGoalKick, verifiedOn: "2026-08-31", reviewAfter: "2027-07-01"),
        "soccer-hard-15": .init(sourceURL: "https://editorial.uefa.com/resources/0290-1bb8779dc8f1-6c1c69c8286e-1000/20240527_circular_2024_25_en_enclosure_1_uefa_clfs_key_amendments_document_en.pdf", verifiedOn: "2026-08-31", reviewAfter: "2027-07-01"),
        "soccer-hard-20": .init(sourceURL: "https://www.fifa.com/en/tournaments/mens/worldcup/canadamexicousa2026/articles/brazil-26-world-cup-records", verifiedOn: "2026-08-31", reviewAfter: "2030-01-01"),
        "soccer-hard-26": .init(sourceURL: "https://www.uefa.com/uefachampionsleague/news/0275-1541637ad1db-88aeeefefefd-1000--all-time-honours-board-which-teams-have-won-the-european/", verifiedOn: "2026-08-31", reviewAfter: "2027-06-01"),
        "soccer-hard-28": .init(sourceURL: ifabPlayers, verifiedOn: "2026-08-31", reviewAfter: "2027-07-01"),
        "geography-easy-9": .init(sourceURL: "https://population.un.org/wpp/", verifiedOn: "2026-08-31", reviewAfter: "2027-07-15"),
        "geography-medium-42": .init(sourceURL: "https://population.un.org/wpp/", verifiedOn: "2026-09-04", reviewAfter: "2027-07-15"),
        "geography-hard-3": .init(sourceURL: "https://www.timeanddate.com/time/zone/france", verifiedOn: "2026-08-31", reviewAfter: "2027-08-31"),
        "movies-medium-8": .init(sourceURL: "https://www.007.com/sir-roger-moore-1927-2017/", verifiedOn: "2026-08-31", reviewAfter: "2028-01-01"),
        "movies-medium-22": .init(sourceURL: "https://awardsdatabase.oscars.org/", verifiedOn: "2026-08-31", reviewAfter: "2027-02-15")
    ]
}

import Foundation

/// A single authored question, before a stable ID and category are attached.
///
/// Seeds are grouped by category and difficulty in `Sources/EZTriviaCore/Questions`.
/// These are bundled, fixed questions. Each question belongs to a single tier.
struct QuestionSeed: Sendable {
    let prompt: String
    let answers: [String]
    let correctAnswerIndex: Int
    let explanation: String
    let visual: String?
    let sourceURL: String?

    init(_ prompt: String, _ answers: [String], _ correctAnswerIndex: Int, _ explanation: String, visual: String? = nil, sourceURL: String? = nil) {
        self.prompt = prompt
        self.answers = answers
        self.correctAnswerIndex = correctAnswerIndex
        self.explanation = explanation
        self.visual = visual
        self.sourceURL = sourceURL
    }
}

/// The three authored tiers for one category.
struct CategorySeeds: Sendable {
    let easy: [QuestionSeed]
    let medium: [QuestionSeed]
    let hard: [QuestionSeed]

    subscript(difficulty: TriviaDifficulty) -> [QuestionSeed] {
        switch difficulty {
        case .easy: easy
        case .medium: medium
        case .hard: hard
        }
    }
}

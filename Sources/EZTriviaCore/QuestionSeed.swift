import Foundation

/// A single authored question, before a stable ID and category are attached.
///
/// Seeds are grouped by category and difficulty in `Sources/EZTriviaCore/Questions`.
/// Every seed is written by hand: there is no generation step, and no seed is
/// reused across difficulties.
struct QuestionSeed: Sendable {
    let prompt: String
    let answers: [String]
    let correctAnswerIndex: Int
    let explanation: String
    let visual: String?

    init(_ prompt: String, _ answers: [String], _ correctAnswerIndex: Int, _ explanation: String, visual: String? = nil) {
        self.prompt = prompt
        self.answers = answers
        self.correctAnswerIndex = correctAnswerIndex
        self.explanation = explanation
        self.visual = visual
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

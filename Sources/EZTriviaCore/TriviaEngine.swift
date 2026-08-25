import Foundation

/// Difficulty weighting for every score the app reports.
///
/// One table for both leaderboards, because they are answering the same
/// question in different time windows: what is a correct answer worth? A hard
/// question is worth more than an easy one in a daily round and in a lifetime
/// total alike, and two tables would eventually disagree.
public enum Scoring {
    public static func points(for question: TriviaQuestion) -> Int {
        points(for: question.difficulty)
    }

    public static func points(for difficulty: TriviaDifficulty) -> Int {
        switch difficulty {
        case .easy: 100
        case .medium: 150
        case .hard: 250
        }
    }
}

public struct TriviaEngine: Sendable {
    public private(set) var questions: [TriviaQuestion]
    public private(set) var currentIndex = 0
    public private(set) var score = 0
    public private(set) var selectedAnswerIndex: Int?

    /// Difficulty-weighted score for this round.
    ///
    /// Feeds the daily leaderboard directly, and is added to the category's
    /// lifetime total at the end of an ordinary round.
    public private(set) var points = 0

    /// Whether each answered question was correct, in the order they were
    /// asked. This is what the shareable result grid is drawn from, so it has
    /// to survive to the end of the round rather than being recomputed.
    public private(set) var outcomes: [Bool] = []

    public init(questions: [TriviaQuestion]) {
        self.questions = questions
    }

    public var currentQuestion: TriviaQuestion? {
        questions.indices.contains(currentIndex) ? questions[currentIndex] : nil
    }

    public var isRoundComplete: Bool { currentIndex >= questions.count }
    public var progress: Double { questions.isEmpty ? 0 : min(Double(currentIndex) / Double(questions.count), 1) }

    @discardableResult
    public mutating func answer(_ index: Int) -> Bool {
        guard selectedAnswerIndex == nil, let question = currentQuestion, question.answers.indices.contains(index) else { return false }
        selectedAnswerIndex = index
        let correct = index == question.correctAnswerIndex
        if correct {
            score += 1
            points += Scoring.points(for: question)
        }
        outcomes.append(correct)
        return correct
    }

    @discardableResult
    public mutating func advance() -> Bool {
        guard selectedAnswerIndex != nil, !isRoundComplete else { return false }
        currentIndex += 1
        selectedAnswerIndex = nil
        return true
    }
}

public enum QuestionPicker {
    /// Builds one round.
    ///
    /// Unseen questions are always preferred. When the unseen pool is too small
    /// to fill a round, it is topped up with already-seen questions rather than
    /// discarded — the previous behaviour threw away every unseen question the
    /// moment the pool ran low, so a player near the end of a category kept
    /// being served questions they had just answered.
    ///
    /// Answer order is shuffled per round so a replayed question does not have
    /// its correct answer in the same position.
    public static func round(
        category: TriviaCategory,
        difficulty: TriviaDifficulty = .easy,
        count: Int = 10,
        excluding excludedIDs: Set<String> = [],
        using bank: [TriviaQuestion] = QuestionBank.all
    ) -> [TriviaQuestion] {
        let matching = bank.filter { $0.category == category && $0.difficulty == difficulty }
        let unseen = matching.filter { !excludedIDs.contains($0.id) }

        var pool = unseen.shuffled()
        if pool.count < count {
            let chosen = Set(pool.map(\.id))
            pool += matching.filter { !chosen.contains($0.id) }.shuffled()
        }

        return pool.prefix(count).map { $0.shufflingAnswers() }
    }

    /// The number of distinct questions available for a category and difficulty.
    public static func availableCount(
        category: TriviaCategory,
        difficulty: TriviaDifficulty,
        using bank: [TriviaQuestion] = QuestionBank.all
    ) -> Int {
        bank.count { $0.category == category && $0.difficulty == difficulty }
    }
}

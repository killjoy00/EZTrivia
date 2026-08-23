import Foundation

public struct TriviaEngine: Sendable {
    public private(set) var questions: [TriviaQuestion]
    public private(set) var currentIndex = 0
    public private(set) var score = 0
    public private(set) var selectedAnswerIndex: Int?

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
        if correct { score += 1 }
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
    public static func round(category: TriviaCategory, difficulty: TriviaDifficulty = .easy, count: Int = 10, excluding excludedIDs: Set<String> = [], using bank: [TriviaQuestion] = QuestionBank.all) -> [TriviaQuestion] {
        let matching = bank.filter { $0.category == category && $0.difficulty == difficulty }
        let unseen = matching.filter { !excludedIDs.contains($0.id) }
        let pool = unseen.count >= count ? unseen : matching
        return Array(pool.shuffled().prefix(count))
    }
}

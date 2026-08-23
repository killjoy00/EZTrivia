import Testing
@testable import EZTriviaCore

@Test func correctAnswerIncreasesScoreOnce() {
    let question = TriviaQuestion(id: "1", category: .science, prompt: "Test?", answers: ["Yes", "No"], correctAnswerIndex: 0, explanation: "Yes.")
    var engine = TriviaEngine(questions: [question])
    let firstAnswer = engine.answer(0)
    #expect(firstAnswer)
    #expect(engine.score == 1)
    let repeatedAnswer = engine.answer(0)
    #expect(!repeatedAnswer)
    #expect(engine.score == 1)
}

@Test func cannotAdvanceBeforeAnswering() {
    let question = TriviaQuestion(id: "1", category: .history, prompt: "Test?", answers: ["A", "B"], correctAnswerIndex: 1, explanation: "B.")
    var engine = TriviaEngine(questions: [question])
    let earlyAdvance = engine.advance()
    #expect(!earlyAdvance)
    #expect(engine.currentIndex == 0)
    let incorrectAnswer = engine.answer(0)
    #expect(!incorrectAnswer)
    let validAdvance = engine.advance()
    #expect(validAdvance)
    #expect(engine.isRoundComplete)
}

@Test func pickerReturnsRequestedCategoryAndUniqueQuestions() {
    let round = QuestionPicker.round(category: .flags, count: 10)
    #expect(round.count == 10)
    #expect(Set(round.map(\.id)).count == 10)
    #expect(round.allSatisfy { $0.category == .flags })
}

@Test func everyCategoryHasEnoughValidQuestions() {
    #expect(TriviaCategory.allCases.count == 11)
    for category in TriviaCategory.allCases {
        let questions = QuestionBank.all.filter { $0.category == category }
        #expect(questions.count == 150)
        for difficulty in TriviaDifficulty.allCases {
            #expect(questions.count(where: { $0.difficulty == difficulty }) == 50)
        }
        #expect(questions.allSatisfy { $0.answers.count == 4 })
        #expect(questions.allSatisfy { $0.answers.indices.contains($0.correctAnswerIndex) })
    }
}

@Test func flagQuestionsIncludeAVisualAndCountryOptions() {
    let flagQuestions = QuestionBank.all.filter { $0.category == .flags }
    #expect(flagQuestions.allSatisfy { $0.visual?.hasPrefix("flag-") == true })
    #expect(flagQuestions.allSatisfy { $0.prompt == "Which country does this flag represent?" })
    #expect(flagQuestions.allSatisfy { $0.answers.count == 4 })
}

@Test func basketballHasFiftyQuestionsAtEveryDifficulty() {
    for difficulty in TriviaDifficulty.allCases {
        let questions = QuestionBank.all.filter { $0.category == .basketball && $0.difficulty == difficulty }
        #expect(questions.count == 50)
    }
}

@Test func leaderboardPercentageRoundsCorrectly() {
    #expect(LeaderboardEntry(category: .movies, score: 2, total: 3).percentage == 67)
    #expect(LeaderboardEntry(category: .movies, score: 0, total: 0).percentage == 0)
}

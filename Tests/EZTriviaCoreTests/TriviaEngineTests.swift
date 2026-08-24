import Foundation
import Testing
@testable import EZTriviaCore

// MARK: - Engine

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

@Test func leaderboardPercentageRoundsCorrectly() {
    #expect(LeaderboardEntry(category: .movies, score: 2, total: 3).percentage == 67)
    #expect(LeaderboardEntry(category: .movies, score: 0, total: 0).percentage == 0)
}

@Test func legacyLeaderboardEntryDecodesWithoutDifficulty() throws {
    let json = #"{"id":"00000000-0000-0000-0000-000000000001","category":"movies","score":2,"total":3,"date":0}"#
    let entry = try JSONDecoder().decode(LeaderboardEntry.self, from: Data(json.utf8))
    #expect(entry.difficulty == nil)
    #expect(entry.percentage == 67)
}

// MARK: - Round construction

@Test func pickerReturnsRequestedCategoryAndUniqueQuestions() {
    for category in TriviaCategory.allCases {
        for difficulty in TriviaDifficulty.allCases {
            let round = QuestionPicker.round(category: category, difficulty: difficulty, count: 10)
            #expect(round.count == 10)
            #expect(Set(round.map(\.id)).count == 10)
            #expect(round.allSatisfy { $0.category == category })
            #expect(round.allSatisfy { $0.difficulty == difficulty })
        }
    }
}

@Test func pickerPrefersUnseenQuestionsButStillFillsTheRound() {
    let available = QuestionPicker.availableCount(category: .animals, difficulty: .easy)
    // Exclude everything but four questions: the round must still be full, and
    // must contain all four unseen questions rather than discarding them.
    let all = QuestionBank.all.filter { $0.category == .animals && $0.difficulty == .easy }
    let unseen = Set(all.prefix(4).map(\.id))
    let excluded = Set(all.map(\.id)).subtracting(unseen)

    let round = QuestionPicker.round(category: .animals, difficulty: .easy, count: 10, excluding: excluded)
    #expect(round.count == 10)
    #expect(Set(round.map(\.id)).count == 10)
    #expect(unseen.isSubset(of: Set(round.map(\.id))))
    #expect(available >= 10)
}

@Test func pickerNeverRepeatsAQuestionWithinARound() {
    for _ in 0..<50 {
        let round = QuestionPicker.round(category: .flags, difficulty: .hard, count: 10)
        #expect(Set(round.map(\.id)).count == round.count)
    }
}

@Test func answerShufflingPreservesTheCorrectAnswer() {
    for question in QuestionBank.all {
        let expected = question.answers[question.correctAnswerIndex]
        for _ in 0..<5 {
            let shuffled = question.shufflingAnswers()
            #expect(shuffled.answers.count == question.answers.count)
            #expect(Set(shuffled.answers) == Set(question.answers))
            #expect(shuffled.answers[shuffled.correctAnswerIndex] == expected)
        }
    }
}

// MARK: - Bank integrity

@Test func everyQuestionIsWellFormed() {
    for question in QuestionBank.all {
        #expect(question.answers.count == 4, "\(question.id) has \(question.answers.count) answers")
        #expect(question.answers.indices.contains(question.correctAnswerIndex), "\(question.id) index out of range")
        #expect(Set(question.answers).count == 4, "\(question.id) has duplicate answers")
        #expect(!question.prompt.isEmpty, "\(question.id) has an empty prompt")
        #expect(!question.explanation.isEmpty, "\(question.id) has no explanation")
        #expect(question.answers.allSatisfy { !$0.isEmpty }, "\(question.id) has an empty answer")
    }
}

@Test func questionIDsAreUnique() {
    let ids = QuestionBank.all.map(\.id)
    #expect(Set(ids).count == ids.count)
}

/// The regression this whole rewrite exists for: the old bank held 12 distinct
/// questions per category, duplicated ~50 times per tier and repeated verbatim
/// across easy, medium, and hard.
@Test func noQuestionTextIsEverRepeated() {
    var seen: [String: String] = [:]
    for question in QuestionBank.all where question.category != .flags {
        if let first = seen[question.prompt] {
            Issue.record("Prompt repeated in \(first) and \(question.id): \(question.prompt)")
        }
        seen[question.prompt] = question.id
    }
}

@Test func difficultyTiersShareNoQuestions() {
    for category in TriviaCategory.allCases where category != .flags {
        let byTier = TriviaDifficulty.allCases.map { difficulty in
            Set(QuestionBank.all.filter { $0.category == category && $0.difficulty == difficulty }.map(\.prompt))
        }
        #expect(byTier[0].isDisjoint(with: byTier[1]), "\(category) shares prompts between easy and medium")
        #expect(byTier[1].isDisjoint(with: byTier[2]), "\(category) shares prompts between medium and hard")
        #expect(byTier[0].isDisjoint(with: byTier[2]), "\(category) shares prompts between easy and hard")
    }
}

@Test func everyCategoryHasEnoughQuestionsAtEveryDifficulty() {
    #expect(TriviaCategory.allCases.count == 11)
    for category in TriviaCategory.allCases where category != .flags {
        for difficulty in TriviaDifficulty.allCases {
            let count = QuestionPicker.availableCount(category: category, difficulty: difficulty)
            #expect(count == 30, "\(category) \(difficulty) has \(count) questions")
        }
    }
}

// MARK: - Flags

@Test func flagQuestionsAreWellFormed() {
    let flagQuestions = QuestionBank.all.filter { $0.category == .flags }
    #expect(flagQuestions.count == FlagCatalog.askable.count)
    #expect(flagQuestions.allSatisfy { $0.visual?.hasPrefix("flag-") == true })
    #expect(flagQuestions.allSatisfy { $0.prompt == QuestionBank.flagPrompt })
    #expect(flagQuestions.allSatisfy { !$0.explanation.hasPrefix("This is the flag of ") })
    #expect(flagQuestions.allSatisfy { !$0.explanation.contains("ISO 3166") })
    for difficulty in TriviaDifficulty.allCases {
        let count = flagQuestions.count { $0.difficulty == difficulty }
        #expect(count >= 10, "only \(count) flag questions at \(difficulty)")
    }
}

@Test func everyAskableFlagHasAFunFact() {
    let missing = FlagCatalog.askable.filter { entry in
        guard let fact = FlagFacts.byCode[entry.code] else { return true }
        return fact.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
    #expect(missing.isEmpty, "Missing flag facts for: \(missing.map(\.code).joined(separator: ", "))")
    let askableCodes = Set(FlagCatalog.askable.map(\.code))
    #expect(Set(FlagFacts.byCode.keys) == askableCodes)
}

@Test func flagCatalogCoversEveryShippedImage() {
    #expect(FlagCatalog.all.count == 249)
    #expect(Set(FlagCatalog.all.map(\.code)).count == 249)
    #expect(FlagCatalog.all.allSatisfy { !$0.name.isEmpty })
}

/// These dependencies use artwork identical to another answer in the catalog.
/// Asking about one of them would have no single right answer.
@Test func flagsWithIdenticalArtworkAreNotAsked() {
    let excluded = Set(FlagCatalog.all.filter { !$0.askable }.map(\.code))
    #expect(excluded == ["BV", "HM", "SJ", "MF", "UM"])
    #expect(FlagCatalog.askable.count == 244)
}

/// Names come from the flag catalog, so no answer option should read like the
/// raw ISO 3166 inverted form.
@Test func flagNamesUseCommonForms() {
    let retiredNames = Set(["Ivory Coast", "Cape Verde", "East Timor", "Caribbean Netherlands"])
    for entry in FlagCatalog.all {
        #expect(!entry.name.contains(","), "\(entry.code) uses an inverted ISO name: \(entry.name)")
        #expect(!entry.name.contains("Province of China"), "\(entry.code) uses a politically loaded ISO label")
        #expect(!retiredNames.contains(entry.name), "\(entry.code) uses an outdated or inaccurate display name")
    }
}

/// Measured near-identical pairs must never be offered against each other.
@Test func confusableFlagsAreNeverOfferedTogether() {
    let byName = Dictionary(uniqueKeysWithValues: FlagCatalog.all.map { ($0.name, $0) })
    for question in QuestionBank.all where question.category == .flags {
        let answer = question.answers[question.correctAnswerIndex]
        guard let entry = byName[answer] else {
            Issue.record("\(question.id) answer '\(answer)' is not in the catalog")
            continue
        }
        for option in question.answers where option != answer {
            guard let other = byName[option] else {
                Issue.record("\(question.id) option '\(option)' is not in the catalog")
                continue
            }
            #expect(!entry.confusable.contains(other.code),
                    "\(entry.name) offered against near-identical \(other.name)")
            #expect(!other.confusable.contains(entry.code),
                    "\(other.name) offered against near-identical \(entry.name)")
            #expect(other.askable, "\(question.id) offers non-askable \(other.name)")
        }
    }
}

@Test func flagDistractorsPreferTheSameDifficultyTier() {
    let byName = Dictionary(uniqueKeysWithValues: FlagCatalog.all.map { ($0.name, $0) })
    var sameTier = 0
    var total = 0
    for question in QuestionBank.all where question.category == .flags {
        for option in question.answers {
            guard let entry = byName[option] else { continue }
            total += 1
            if entry.difficulty == question.difficulty { sameTier += 1 }
        }
    }
    #expect(Double(sameTier) / Double(total) > 0.9)
}

/// Easy flags should be recognisable ones. The old bank sorted by ISO code, so
/// Japan and Mexico landed in "hard" while the Cocos Islands landed in "easy".
@Test func wellKnownFlagsAreInTheEasyTier() {
    let easy = Set(FlagCatalog.all.filter { $0.difficulty == .easy }.map(\.code))
    for code in ["US", "GB", "FR", "DE", "IT", "JP", "CA", "MX", "BR", "IN", "CN", "AU"] {
        #expect(easy.contains(code), "\(code) should be an easy flag")
    }
    let hard = Set(FlagCatalog.all.filter { $0.difficulty == .hard }.map(\.code))
    for code in ["CC", "BQ", "CX", "BL", "PN", "TK"] {
        #expect(hard.contains(code), "\(code) should be a hard flag")
    }
}

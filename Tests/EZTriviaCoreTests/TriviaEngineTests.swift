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
    #expect(TriviaCategory.allCases.count == 12)
    for category in TriviaCategory.allCases where category != .flags {
        for difficulty in TriviaDifficulty.allCases {
            let count = QuestionPicker.availableCount(category: category, difficulty: difficulty)
            #expect(count == 40, "\(category) \(difficulty) has \(count) questions")
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

/// Two separate reasons a flag ships but is never asked, asserted together so
/// neither list can be edited by accident.
@Test func flagsWithIdenticalArtworkAreNotAsked() {
    let excluded = Set(FlagCatalog.all.filter { !$0.askable }.map(\.code))

    // Artwork identical to another answer: the question would have no single
    // right answer.
    let identicalArtwork: Set<String> = ["BV", "HM", "SJ", "MF", "UM"]
    // Artwork outdated or contested enough that grading against it is not
    // defensible.
    let contestedArtwork: Set<String> = ["AF", "NC", "EH"]

    #expect(excluded == identicalArtwork.union(contestedArtwork))
    #expect(FlagCatalog.askable.count == 241)
}

/// A withdrawn flag must vanish from the game completely, not just stop being
/// the answer -- it must never turn up as someone else's wrong option either.
@Test func withdrawnFlagsAppearNowhereInTheBank() {
    let withdrawn = Set(FlagCatalog.all.filter { !$0.askable }.map(\.name))
    for question in QuestionBank.all where question.category == .flags {
        for option in question.answers {
            #expect(!withdrawn.contains(option), "\(question.id) offers withdrawn \(option)")
        }
    }
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

// MARK: - Redrawn flag options
//
// Flag questions no longer carry a fixed set of wrong answers. The bank stores
// one set so the CSV export is reviewable, but every round redraws them, so
// these tests cover the redraw rather than the stored row.

/// The reason the feature exists: the same flag asked twice must not arrive
/// with the same three wrong answers, or a player learns the option set instead
/// of the flag.
@Test func flagDistractorsAreRedrawnEachTime() {
    guard let entry = FlagCatalog.askable.first(where: { $0.difficulty == .easy }) else {
        Issue.record("the catalog has no easy flags")
        return
    }

    var generator = SystemRandomNumberGenerator()
    var seen: Set<String> = []
    for _ in 0..<25 {
        let distractors = FlagCatalog.options(for: entry, using: &generator)
            .filter { $0.code != entry.code }
            .map(\.name)
            .sorted()
        seen.insert(distractors.joined(separator: "|"))
    }

    // Three drawn from roughly forty candidates is thousands of possible sets,
    // so an occasional repeat is plausible and a near-constant set is not.
    #expect(seen.count >= 5, "only \(seen.count) distinct option sets in 25 draws")
}

@Test func redrawnFlagOptionsStayWellFormed() {
    var generator = SystemRandomNumberGenerator()

    for entry in FlagCatalog.askable {
        for _ in 0..<5 {
            let options = FlagCatalog.options(for: entry, using: &generator)
            #expect(options.count == 4, "\(entry.code) drew \(options.count) options")
            #expect(Set(options.map(\.code)).count == 4, "\(entry.code) drew a duplicate option")
            #expect(options.contains { $0.code == entry.code }, "\(entry.code) lost its own answer")

            for option in options where option.code != entry.code {
                #expect(option.askable, "\(entry.code) offered withdrawn \(option.code)")
                #expect(FlagCatalog.mayAppearTogether(entry, option),
                        "\(entry.name) offered against near-identical \(option.name)")
            }
        }
    }
}

/// The invariant that keeps the cross-tier fallback in `FlagCatalog.options`
/// unreachable, checked here rather than with a runtime precondition: the bank
/// is built inside a lazily initialised `static let`, so a trap would surface
/// as a crash on launch instead of a failed build.
@Test func everyFlagHasEnoughSameTierAnswers() {
    for entry in FlagCatalog.askable {
        let sameTier = FlagCatalog.askable.count {
            $0.difficulty == entry.difficulty && FlagCatalog.mayAppearTogether(entry, $0)
        }
        #expect(sameTier >= 3, "\(entry.code) has only \(sameTier) safe same-tier answers")
    }
}

/// Given the invariant above, every option is same-tier -- an easy question
/// never offers an obscure dependency as one of its wrong answers.
@Test func redrawnFlagDistractorsAreAlwaysSameTier() {
    var generator = SystemRandomNumberGenerator()

    for entry in FlagCatalog.askable {
        for _ in 0..<3 {
            for option in FlagCatalog.options(for: entry, using: &generator) {
                #expect(option.difficulty == entry.difficulty,
                        "\(entry.code) (\(entry.difficulty)) offered \(option.code) (\(option.difficulty))")
            }
        }
    }
}

/// Authored distractors are replaced, not merely reordered. Builds a question
/// whose stored wrong answers are all from the wrong tier and checks that none
/// of them survives into the round.
@Test func roundCreationReplacesAuthoredFlagDistractors() {
    guard let answer = FlagCatalog.askable.first(where: { $0.difficulty == .easy }) else {
        Issue.record("the catalog has no easy flags")
        return
    }
    let wrongTier = Array(FlagCatalog.askable.filter { $0.difficulty == .hard }.prefix(3))
    #expect(wrongTier.count == 3)

    let authored = TriviaQuestion(
        id: "flags-easy-\(answer.code.lowercased())",
        category: .flags,
        prompt: QuestionBank.flagPrompt,
        difficulty: .easy,
        visual: answer.asset,
        answers: [answer.name] + wrongTier.map(\.name),
        correctAnswerIndex: 0,
        explanation: "Test fact."
    )

    let round = QuestionPicker.round(category: .flags, difficulty: .easy, count: 1, using: [authored])
    #expect(round.count == 1)
    guard let played = round.first else { return }

    #expect(played.answers[played.correctAnswerIndex] == answer.name)
    #expect(Set(played.answers).isDisjoint(with: Set(wrongTier.map(\.name))))
}

/// Redrawing must not disturb anything the round already depends on: the id it
/// is tracked by, the artwork on screen, or which name is correct.
@Test func presentingAFlagQuestionKeepsItsIdentity() {
    var generator = SystemRandomNumberGenerator()

    for question in QuestionBank.all where question.category == .flags {
        let expected = question.answers[question.correctAnswerIndex]
        for _ in 0..<3 {
            let presented = QuestionBank.presenting(question, using: &generator)
            #expect(presented.id == question.id)
            #expect(presented.visual == question.visual)
            #expect(presented.explanation == question.explanation)
            #expect(presented.answers.count == 4)
            #expect(Set(presented.answers).count == 4)
            #expect(presented.answers[presented.correctAnswerIndex] == expected)
        }
    }
}

@Test func presentingANonFlagQuestionOnlyReordersIt() {
    var generator = SystemRandomNumberGenerator()

    for question in QuestionBank.all where question.category != .flags {
        let presented = QuestionBank.presenting(question, using: &generator)
        #expect(presented.id == question.id)
        #expect(Set(presented.answers) == Set(question.answers))
        #expect(presented.answers[presented.correctAnswerIndex]
                == question.answers[question.correctAnswerIndex])
    }
}

/// Ids are derived from the flag's code, so withdrawing or adding a flag cannot
/// renumber every question after it.
@Test func flagQuestionIDsAreDerivedFromTheFlagCode() {
    for question in QuestionBank.all where question.category == .flags {
        guard let entry = QuestionBank.flagEntry(for: question) else {
            Issue.record("\(question.id) has no catalog entry")
            continue
        }
        #expect(question.id == "flags-\(entry.difficulty.rawValue)-\(entry.code.lowercased())")
    }
}

/// The same guarantee as `redrawnFlagDistractorsAreAlwaysSameTier`, but for the
/// authored rows that reach QuestionReview.csv, so a reviewer proofing the file
/// sees the tiering the game actually uses.
@Test func flagDistractorsAreFromTheSameDifficultyTier() {
    let byName = Dictionary(uniqueKeysWithValues: FlagCatalog.all.map { ($0.name, $0) })
    for question in QuestionBank.all where question.category == .flags {
        for option in question.answers {
            guard let entry = byName[option] else {
                Issue.record("\(question.id) option '\(option)' is not in the catalog")
                continue
            }
            #expect(entry.difficulty == question.difficulty,
                    "\(question.id) offers \(entry.difficulty) option \(entry.name)")
        }
    }
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

// MARK: - Practice rounds

@Test func questionsAreUniquelyAddressableByID() {
    #expect(QuestionBank.byID.count == QuestionBank.all.count)
    for question in QuestionBank.all {
        #expect(QuestionBank.byID[question.id]?.prompt == question.prompt)
    }
}

/// Practice serves the longest-outstanding miss first. Reordering here would
/// let a growing backlog hide behind whatever the player missed most recently.
@Test func practiceRoundPreservesTheOrderOfMisses() {
    let ids = Array(QuestionBank.all.prefix(6).map(\.id))
    let round = QuestionPicker.practiceRound(ids: ids)
    #expect(round.map(\.id) == ids)
}

/// An id that no longer resolves -- a question withdrawn by an app update --
/// is skipped rather than shortening the round by crashing on it.
@Test func practiceRoundSkipsUnknownIDs() {
    let real = QuestionBank.all[0].id
    let round = QuestionPicker.practiceRound(ids: ["not-a-question", real, "also-gone"])
    #expect(round.map(\.id) == [real])
}

@Test func practiceRoundIsCappedAtTheRequestedCount() {
    let ids = Array(QuestionBank.all.prefix(25).map(\.id))
    #expect(QuestionPicker.practiceRound(ids: ids).count == 10)
    #expect(QuestionPicker.practiceRound(ids: ids, count: 4).count == 4)
    #expect(QuestionPicker.practiceRound(ids: []).isEmpty)
}

/// Every question handed to the player must still be answerable: presenting a
/// question redraws its options, and a practice round goes through the same
/// path as an ordinary one.
@Test func practiceRoundQuestionsKeepTheirCorrectAnswer() {
    let ids = Array(QuestionBank.all.prefix(10).map(\.id))
    for question in QuestionPicker.practiceRound(ids: ids) {
        #expect(question.answers.count == 4)
        #expect(Set(question.answers).count == 4)
        #expect(question.answers.indices.contains(question.correctAnswerIndex))
        let original = QuestionBank.byID[question.id]
        #expect(original != nil)
        if let original {
            // Flag questions redraw their wrong answers, so only the keyed
            // answer is guaranteed to survive presentation.
            #expect(question.answers[question.correctAnswerIndex]
                    == original.answers[original.correctAnswerIndex])
        }
    }
}

// MARK: - Answer quality

/// The correct answer must not be identifiable from its length alone.
///
/// A player who always picks the longest option should do no better than one
/// guessing at random. Before the 1.0.1 distractor rewrite the hard tier sat at
/// 73.8% -- three times chance, which made the whole tier answerable without
/// knowing anything. Flags are excluded: their options are country names drawn
/// from the catalog, so their lengths are not authored.
@Test func theLongestAnswerIsNotUsuallyTheCorrectOne() {
    for difficulty in TriviaDifficulty.allCases {
        let tier = QuestionBank.all.filter { $0.difficulty == difficulty && $0.category != .flags }
        let tell = tier.count { question in
            let longest = question.answers.max { $0.count < $1.count }
            return longest == question.answers[question.correctAnswerIndex]
        }
        let rate = Double(tell) / Double(tier.count)
        #expect(rate < 0.40, "\(difficulty.title): correct answer is longest in \(tell)/\(tier.count)")
    }
}

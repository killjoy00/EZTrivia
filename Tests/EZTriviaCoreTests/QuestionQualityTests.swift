import Foundation
import Testing
@testable import EZTriviaCore

private let newCategories: Set<TriviaCategory> = [.literature, .art, .mythology, .videoGames]

private func normalizedTokens(_ value: String) -> Set<String> {
    let stopWords: Set<String> = [
        "a", "an", "and", "are", "for", "from", "in", "is", "it", "of",
        "on", "or", "that", "the", "this", "to", "was", "were", "with"
    ]
    return Set(
        value.lowercased()
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty && !stopWords.contains($0) }
    )
}

private func isConspicuousLongestAnswer(_ question: TriviaQuestion) -> Bool {
    let lengths = question.answers.map(\.count)
    guard lengths.count >= 2 else { return false }
    let correctLength = lengths[question.correctAnswerIndex]
    let ordered = lengths.sorted(by: >)
    guard correctLength == ordered[0],
          lengths.count(where: { $0 == ordered[0] }) == 1 else { return false }
    let nextLongest = max(ordered[1], 1)
    return correctLength - nextLongest >= 5
        && Double(correctLength) / Double(nextLongest) >= 1.25
}

/// The pool-level 35% check catches broad bias; this catches a smaller set of
/// individual questions whose answer is conspicuously longer than every
/// distractor. Existing content gets a strict budget instead of a permanent
/// allowlist, so future rewrites can improve it without making old IDs sacred.
@Test func conspicuousLongestAnswersStayWithinAnEditorialBudget() {
    var outlierCount = 0
    var textQuestionCount = 0

    for category in TriviaCategory.allCases where category != .flags {
        for difficulty in TriviaDifficulty.allCases {
            let pool = QuestionBank.all.filter {
                $0.category == category && $0.difficulty == difficulty
            }
            let outliers = pool.filter(isConspicuousLongestAnswer)
            #expect(outliers.count <= 10,
                    "\(category.title) / \(difficulty.title) has \(outliers.count) conspicuous length giveaways: \(outliers.map(\.id))")
            outlierCount += outliers.count
            textQuestionCount += pool.count
        }
    }

    let rate = Double(outlierCount) / Double(textQuestionCount)
    #expect(rate <= 0.09, "\(outlierCount) of \(textQuestionCount) text questions are conspicuous length giveaways")
}

/// Repeating an answer is sometimes legitimate, but a fourth occurrence in a
/// forty-question pool is a signal that the pool is testing one concept too
/// often. This complements exact-prompt duplicate detection.
@Test func noCorrectAnswerDominatesOnePool() {
    for category in TriviaCategory.allCases where category != .flags {
        for difficulty in TriviaDifficulty.allCases {
            let pool = QuestionBank.all.filter {
                $0.category == category && $0.difficulty == difficulty
            }
            let grouped = Dictionary(grouping: pool) { question in
                normalizedTokens(question.answers[question.correctAnswerIndex])
                    .sorted().joined(separator: " ")
            }
            for (answer, questions) in grouped {
                #expect(questions.count <= 3,
                        "\(category.title) / \(difficulty.title) repeats '\(answer)' in \(questions.map(\.id))")
            }
        }
    }
}

/// A useful explanation normally names or clearly restates the answer. Token
/// overlap cannot judge prose, but it reliably catches regressions where an
/// explanation accidentally describes a distractor, as the old raisin row did.
@Test func explanationsUsuallyReinforceTheCorrectAnswer() {
    let textQuestions = QuestionBank.all.filter { $0.category != .flags }
    let reinforced = textQuestions.filter { question in
        let answer = normalizedTokens(question.answers[question.correctAnswerIndex])
        let explanation = normalizedTokens(question.explanation)
        return !answer.isDisjoint(with: explanation)
    }
    #expect(Double(reinforced.count) / Double(textQuestions.count) >= 0.60)

    for category in newCategories {
        let questions = textQuestions.filter { $0.category == category }
        let categoryReinforced = questions.count { question in
            !normalizedTokens(question.answers[question.correctAnswerIndex])
                .isDisjoint(with: normalizedTokens(question.explanation))
        }
        #expect(Double(categoryReinforced) / Double(questions.count) >= 0.85,
                "\(category.title) explanations reinforce only \(categoryReinforced) of \(questions.count) answers")
    }
}

/// Keeps newly authored copy short enough for phone layouts and aligned with
/// the repository's English (U.S.) storefront language. Official quoted names
/// elsewhere in the older bank are intentionally outside this migration guard.
@Test func newCategoryCopyFollowsTheHouseStyle() {
    let britishSpellings: Set<String> = [
        "colour", "colours", "centre", "centres", "defence", "offence",
        "metre", "metres", "organise", "organised", "travelling"
    ]

    for question in QuestionBank.all where newCategories.contains(question.category) {
        #expect(question.prompt.hasSuffix("?"), "\(question.id) is not phrased as a question")
        #expect(question.prompt.split(whereSeparator: \.isWhitespace).count <= 20,
                "\(question.id) prompt is too long for compact phone layouts")
        #expect((8...24).contains(question.explanation.split(whereSeparator: \.isWhitespace).count),
                "\(question.id) explanation falls outside the concise teaching style")
        #expect(question.answers.allSatisfy { $0.split(whereSeparator: \.isWhitespace).count <= 12 },
                "\(question.id) has an overlong answer choice")

        let copy = ([question.prompt, question.explanation] + question.answers).joined(separator: " ")
        let disallowed = normalizedTokens(copy).intersection(britishSpellings)
        #expect(disallowed.isEmpty, "\(question.id) uses non-house spellings: \(disallowed)")
    }
}

@Test func newCategoryPoolsRemainVaried() {
    for category in newCategories {
        for difficulty in TriviaDifficulty.allCases {
            let answers = QuestionBank.all
                .filter { $0.category == category && $0.difficulty == difficulty }
                .map { normalizedTokens($0.answers[$0.correctAnswerIndex]).sorted().joined(separator: " ") }
            // Expressed as a budget of repeats rather than a fixed count, so
            // growing a pool does not quietly loosen the rule.
            #expect(Set(answers).count >= answers.count - 4,
                    "\(category.title) / \(difficulty.title) repeats too many keyed answers")
        }
    }
}

/// CI becomes the calendar reminder for rules, rankings, and records. When a
/// deadline arrives, a maintainer must verify the official source and move the
/// dates forward—or update the question—before another build can ship.
@Test func populationRankingQuestionsHaveReviewMetadata() {
    let volatilePhrases = ["largest population", "most populous"]
    for question in QuestionBank.all where question.category != .flags {
        let copy = (question.prompt + " " + question.explanation).lowercased()
        guard volatilePhrases.contains(where: copy.contains) else { continue }
        #expect(QuestionReviewRegistry.byQuestionID[question.id] != nil,
                "\(question.id) is a population-ranking fact without scheduled review metadata")
    }
}

@Test func timeSensitiveQuestionReviewsAreCurrent() throws {
    let expectedIDs: Set<String> = [
        "basketball-easy-45", "basketball-medium-7", "basketball-medium-8", "basketball-medium-14", "basketball-hard-19", "basketball-hard-25",
        "football-medium-5", "football-medium-18", "football-hard-11", "football-hard-20", "football-hard-26",
        "soccer-easy-12", "soccer-easy-49", "soccer-easy-20", "soccer-medium-13", "soccer-medium-21", "soccer-hard-15", "soccer-hard-20",
        "soccer-hard-26", "soccer-hard-28", "geography-easy-9", "geography-medium-42", "geography-hard-3",
        "movies-medium-8", "movies-medium-22"
    ]
    #expect(Set(QuestionReviewRegistry.byQuestionID.keys) == expectedIDs)

    let bankIDs = Set(QuestionBank.all.map(\.id))
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    formatter.dateFormat = "yyyy-MM-dd"
    let today = Calendar(identifier: .gregorian).startOfDay(for: Date())

    for (id, metadata) in QuestionReviewRegistry.byQuestionID {
        #expect(bankIDs.contains(id), "Review metadata refers to missing question \(id)")
        #expect(URL(string: metadata.sourceURL)?.scheme == "https", "\(id) needs an HTTPS source")
        let verified = try #require(formatter.date(from: metadata.verifiedOn), "\(id) has an invalid verified date")
        let review = try #require(formatter.date(from: metadata.reviewAfter), "\(id) has an invalid review date")
        #expect(review >= verified, "\(id) review deadline precedes verification")
        #expect(review >= today, "\(id) was due for re-verification on \(metadata.reviewAfter)")
    }
}

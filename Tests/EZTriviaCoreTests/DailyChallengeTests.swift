import Foundation
import Testing
@testable import EZTriviaCore

// MARK: - Determinism
//
// The daily only works if two players get the same round. These tests are the
// guard on that: any change to selection, seeding, or the question bank that
// breaks reproducibility fails here rather than in a screenshot argument
// between two players who saw different questions.

@Test func aDailyIsIdenticalEveryTimeItIsBuilt() {
    let first = DailyChallenge.challenge(for: 412)
    let second = DailyChallenge.challenge(for: 412)

    #expect(first.questions.map(\.id) == second.questions.map(\.id))
    // Answer order has to match too. A shared screenshot showing "B" is
    // meaningless if B is a different option on someone else's phone.
    #expect(first.questions.map(\.answers) == second.questions.map(\.answers))
    #expect(first.questions.map(\.correctAnswerIndex) == second.questions.map(\.correctAnswerIndex))
}

/// Flag questions draw their wrong answers at presentation time, so the daily
/// is only fair if that draw is seeded from the day rather than from system
/// randomness. `aDailyIsIdenticalEveryTimeItIsBuilt` would pass on a day that
/// happens to contain no flag question, so this one seeks a day that does.
@Test func aDailyWithAFlagQuestionIsStillIdenticalEveryTime() {
    guard let day = (0..<60).first(where: { day in
        DailyChallenge.challenge(for: day).questions.contains { $0.category == .flags }
    }) else {
        Issue.record("no daily in the first 60 days contains a flag question")
        return
    }

    let first = DailyChallenge.challenge(for: day)
    let second = DailyChallenge.challenge(for: day)
    let flags = first.questions.filter { $0.category == .flags }

    #expect(!flags.isEmpty)
    #expect(first.questions.map(\.answers) == second.questions.map(\.answers))
    #expect(first.questions.map(\.correctAnswerIndex) == second.questions.map(\.correctAnswerIndex))
}

@Test func consecutiveDaysAreNotCorrelated() {
    // Consecutive seeds fed straight into an xorshift produce visibly related
    // streams, which would show as near-identical rounds two days running.
    //
    // Deliberately not asserting zero overlap between neighbouring days: two
    // independent draws of ten from ~1,445 questions collide by chance about
    // seven percent of the time, so that test would fail spuriously. What
    // decorrelation actually predicts is that consecutive rounds are never the
    // same round, and that ten days keep drawing widely from the bank.
    let days = (500..<510).map { DailyChallenge.challenge(for: $0) }

    for (index, day) in days.enumerated().dropFirst() {
        #expect(days[index - 1].questions.map(\.id) != day.questions.map(\.id))
    }

    let distinct = Set(days.flatMap { $0.questions.map(\.id) })
    #expect(distinct.count > 80)
}

@Test func everyDayIsAFullRoundOfDistinctQuestions() {
    for day in stride(from: 0, to: 2000, by: 137) {
        let challenge = DailyChallenge.challenge(for: day)
        #expect(challenge.questions.count == DailyChallenge.questionCount)
        #expect(Set(challenge.questions.map(\.id)).count == DailyChallenge.questionCount)
    }
}

@Test func noDayRepeatsACategory() {
    for day in stride(from: 0, to: 900, by: 61) {
        let categories = DailyChallenge.challenge(for: day).questions.map(\.category)
        #expect(Set(categories).count == categories.count)
    }
}

@Test func everyDayFollowsTheSameDifficultyRamp() {
    for day in stride(from: 0, to: 900, by: 53) {
        let difficulties = DailyChallenge.challenge(for: day).questions.map(\.difficulty)
        #expect(difficulties == DailyChallenge.ramp)
    }
}

// MARK: - Day numbering

@Test func dayNumberAdvancesByOnePerCalendarDay() {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: "UTC")!

    let start = calendar.date(from: DateComponents(year: 2026, month: 6, day: 1, hour: 9))!
    let nextDay = calendar.date(byAdding: .day, value: 1, to: start)!

    #expect(DailyChallenge.day(for: nextDay, in: calendar) == DailyChallenge.day(for: start, in: calendar) + 1)
}

@Test func theDayDoesNotChangeDuringACalendarDay() {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: "UTC")!

    let earlyMorning = calendar.date(from: DateComponents(year: 2026, month: 6, day: 1, hour: 0, minute: 1))!
    let lateNight = calendar.date(from: DateComponents(year: 2026, month: 6, day: 1, hour: 23, minute: 59))!

    #expect(DailyChallenge.day(for: earlyMorning, in: calendar) == DailyChallenge.day(for: lateNight, in: calendar))
}

// MARK: - Scoring

@Test func hardQuestionsAreWorthMoreThanEasyOnes() {
    #expect(Scoring.points(for: .hard) > Scoring.points(for: .medium))
    #expect(Scoring.points(for: .medium) > Scoring.points(for: .easy))
}

@Test func aPerfectDailyStaysInsideTheLeaderboardRange() {
    // The Game Center leaderboard is provisioned with a 0-2000 range. A round
    // that could score above it would be rejected on submission.
    for day in stride(from: 0, to: 700, by: 37) {
        #expect(DailyChallenge.challenge(for: day).totalPoints <= 2000)
    }
}

@Test func lifetimePointsOnlyEverGrow() {
    // The property that makes a lifetime total work as a BEST_SCORE
    // leaderboard: because it is monotonic, the best score Game Center has
    // ever seen is the current total, so a late or out-of-order submission
    // can never lower a player's standing.
    let question = TriviaQuestion(id: "q", category: .music, prompt: "?", difficulty: .medium, answers: ["A", "B"], correctAnswerIndex: 0, explanation: "")

    var lifetime = 0
    for round in 0..<25 {
        var engine = TriviaEngine(questions: [question])
        // Alternate right and wrong, so some rounds add nothing at all.
        _ = engine.answer(round % 2)
        let updated = lifetime + engine.points
        #expect(updated >= lifetime)
        lifetime = updated
    }

    #expect(lifetime == 13 * Scoring.points(for: .medium))
}

@Test func aLifetimeTotalStaysInsideTheLeaderboardRange() {
    // Category boards are provisioned with a 0-1,000,000 range. A perfect
    // round is 2,500 points, so that covers 400 flawless rounds in a single
    // category before anything would be rejected.
    let perfectRound = DailyChallenge.questionCount * Scoring.points(for: .hard)
    #expect(perfectRound == 2_500)
    #expect(1_000_000 / perfectRound >= 400)
}

@Test func engineAccumulatesWeightedPoints() {
    let easy = TriviaQuestion(id: "e", category: .science, prompt: "?", difficulty: .easy, answers: ["A", "B"], correctAnswerIndex: 0, explanation: "")
    let hard = TriviaQuestion(id: "h", category: .science, prompt: "?", difficulty: .hard, answers: ["A", "B"], correctAnswerIndex: 0, explanation: "")
    var engine = TriviaEngine(questions: [easy, hard])

    _ = engine.answer(0)
    _ = engine.advance()
    _ = engine.answer(1)

    #expect(engine.score == 1)
    #expect(engine.points == Scoring.points(for: .easy))
    #expect(engine.outcomes == [true, false])
}

// MARK: - Streaks

@Test func streakCountsBackFromToday() {
    #expect(DailyStreak.current(playedDays: [10, 11, 12], today: 12) == 3)
}

@Test func aStreakSurvivesUntilAWholeDayIsMissed() {
    // Played through yesterday, today not played yet. The streak is still
    // alive: the player has all of today to keep it.
    #expect(DailyStreak.current(playedDays: [10, 11, 12], today: 13) == 3)
    // Two days now missing, so it is genuinely broken.
    #expect(DailyStreak.current(playedDays: [10, 11, 12], today: 14) == 0)
}

@Test func aGapBreaksTheStreak() {
    #expect(DailyStreak.current(playedDays: [1, 2, 5, 6, 7], today: 7) == 3)
}

@Test func noPlaysMeansNoStreak() {
    #expect(DailyStreak.current(playedDays: [], today: 9) == 0)
}

// MARK: - Share text

@Test func theShareGridMatchesTheOutcomes() {
    let text = RoundSummary.daily(day: 7, outcomes: [true, false, true], points: 350, streak: 4)

    #expect(text.contains("#7"))
    #expect(text.contains("2/3"))
    #expect(text.contains("4 day streak"))
    #expect(text.contains(RoundSummary.grid([true, false, true])))
    // The grid is squares rather than words precisely so the text can be
    // posted publicly without spoiling the round for anyone who has not
    // played it yet. Nothing question-shaped should survive into it.
    #expect(!text.contains("?"))
}

@Test func shareTextLinksBackToTheApp() {
    // The link is the whole growth loop: someone who receives a shared score
    // and does not have the app needs a way to get it.
    let daily = RoundSummary.daily(day: 3, outcomes: [true, true], points: 200, streak: 1)
    let round = RoundSummary.round(category: .music, difficulty: .easy, outcomes: [true])

    #expect(daily.contains(RoundSummary.appStoreURL))
    #expect(round.contains(RoundSummary.appStoreURL))
    // Last line, so the text reads as a result with a link under it rather
    // than an advert with a score attached.
    #expect(daily.hasSuffix(RoundSummary.appStoreURL))
    #expect(round.hasSuffix(RoundSummary.appStoreURL))
}

@Test func aSingleDayIsNotDescribedAsAStreak() {
    let text = RoundSummary.daily(day: 1, outcomes: [true], points: 100, streak: 1)
    #expect(!text.contains("streak"))
}

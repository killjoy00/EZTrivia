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
    #expect(DailyScoring.points(for: .hard) > DailyScoring.points(for: .medium))
    #expect(DailyScoring.points(for: .medium) > DailyScoring.points(for: .easy))
}

@Test func aPerfectDailyStaysInsideTheLeaderboardRange() {
    // The Game Center leaderboard is provisioned with a 0-2000 range. A round
    // that could score above it would be rejected on submission.
    for day in stride(from: 0, to: 700, by: 37) {
        #expect(DailyChallenge.challenge(for: day).totalPoints <= 2000)
    }
}

@Test func engineAccumulatesWeightedPoints() {
    let easy = TriviaQuestion(id: "e", category: .science, prompt: "?", difficulty: .easy, answers: ["A", "B"], correctAnswerIndex: 0, explanation: "")
    let hard = TriviaQuestion(id: "h", category: .science, prompt: "?", difficulty: .hard, answers: ["A", "B"], correctAnswerIndex: 0, explanation: "")
    var engine = TriviaEngine(questions: [easy, hard])

    _ = engine.answer(0)
    _ = engine.advance()
    _ = engine.answer(1)

    #expect(engine.score == 1)
    #expect(engine.points == DailyScoring.points(for: .easy))
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

@Test func aSingleDayIsNotDescribedAsAStreak() {
    let text = RoundSummary.daily(day: 1, outcomes: [true], points: 100, streak: 1)
    #expect(!text.contains("streak"))
}

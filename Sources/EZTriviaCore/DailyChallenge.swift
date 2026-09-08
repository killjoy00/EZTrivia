import Foundation

/// The once-a-day round that every player sees an identical copy of.
///
/// The day boundary is the player's own local midnight rather than UTC. Daily
/// #252 (September 9, 2026 in the Gregorian calendar) is the first round built
/// with the repository-owned cross-platform algorithm. Earlier days keep the
/// legacy Swift implementation so updating the app cannot rewrite an already
/// played historical Daily.
public struct DailyChallenge: Sendable, Equatable {
    public static let questionCount = 10

    /// Daily #252 / September 9, 2026. Day numbers are zero-based internally.
    public static let crossPlatformStartDay = 251
    public static let crossPlatformAlgorithmVersion = 2

    /// Frozen roster for the cross-platform Daily contract.
    static let categoryRoster: [TriviaCategory] = [
        .football, .basketball, .soccer, .flags, .history, .science, .movies,
        .tv, .geography, .music, .animals, .food, .literature, .art,
        .mythology, .videoGames
    ]

    static let ramp: [TriviaDifficulty] = [
        .easy, .easy, .easy, .medium, .medium, .medium, .medium, .hard, .hard, .hard
    ]

    public let day: Int
    public let questions: [TriviaQuestion]

    public var displayNumber: Int { day + 1 }
    public var totalPoints: Int { questions.reduce(0) { $0 + Scoring.points(for: $1) } }

    // MARK: - Day numbering

    private static let epoch = DateComponents(year: 2026, month: 1, day: 1)

    /// Raw day numbering in the supplied calendar. Kept public for historical
    /// tests and explicit callers; app-facing code should use `currentDay` so
    /// Daily v2 cannot diverge when an iPhone uses a non-Gregorian display
    /// calendar while Android uses ISO/Gregorian `LocalDate`.
    public static func day(for date: Date, in calendar: Calendar = .current) -> Int {
        guard let epochDate = calendar.date(from: epoch) else { return 0 }
        let from = calendar.startOfDay(for: epochDate)
        let to = calendar.startOfDay(for: date)
        return calendar.dateComponents([.day], from: from, to: to).day ?? 0
    }

    /// The app-facing local day. Before v2 this intentionally preserves the
    /// user's existing Calendar.current behavior. At and after the v2 cutover,
    /// the contract is explicitly Gregorian in the same local time zone as
    /// Android's LocalDate.
    public static func currentDay(
        for date: Date = Date(),
        in legacyCalendar: Calendar = .current
    ) -> Int {
        var gregorian = Calendar(identifier: .gregorian)
        gregorian.timeZone = legacyCalendar.timeZone
        let v2Day = day(for: date, in: gregorian)
        if v2Day >= crossPlatformStartDay { return v2Day }
        return day(for: date, in: legacyCalendar)
    }

    public static func startOfDay(_ day: Int, in calendar: Calendar = .current) -> Date? {
        let effectiveCalendar: Calendar
        if day >= crossPlatformStartDay {
            var gregorian = Calendar(identifier: .gregorian)
            gregorian.timeZone = calendar.timeZone
            effectiveCalendar = gregorian
        } else {
            effectiveCalendar = calendar
        }

        guard let epochDate = effectiveCalendar.date(from: epoch) else { return nil }
        return effectiveCalendar.date(
            byAdding: .day,
            value: day,
            to: effectiveCalendar.startOfDay(for: epochDate)
        )
    }

    // MARK: - Building

    public static func today(
        in calendar: Calendar = .current,
        using bank: [TriviaQuestion] = QuestionBank.all
    ) -> DailyChallenge {
        challenge(for: currentDay(for: Date(), in: calendar), using: bank)
    }

    /// Builds a Daily while preserving the exact legacy behavior before the
    /// cross-platform switchover. From `crossPlatformStartDay` onward every
    /// random choice uses repository-owned modulo/Fisher-Yates behavior that is
    /// implemented identically by Swift and Kotlin.
    public static func challenge(
        for day: Int,
        using bank: [TriviaQuestion] = QuestionBank.all
    ) -> DailyChallenge {
        if day < crossPlatformStartDay {
            return legacyChallenge(for: day, using: bank)
        }
        return crossPlatformChallenge(for: day, using: bank)
    }

    /// The stable v2 contract shared by iOS and Android.
    static func crossPlatformChallenge(
        for day: Int,
        using bank: [TriviaQuestion] = QuestionBank.all
    ) -> DailyChallenge {
        var generator = SeededGenerator(seed: seed(for: day))
        let categories = Array(
            categoryRoster
                .deterministicallyShuffled(using: &generator)
                .prefix(questionCount)
        )

        var questions: [TriviaQuestion] = []
        questions.reserveCapacity(questionCount)
        var used: Set<String> = []

        for slot in 0..<min(questionCount, categories.count) {
            let difficulty = ramp[slot]
            let category = categories[slot]
            let pool = bank.filter {
                $0.category == category && $0.difficulty == difficulty && !used.contains($0.id)
            }
            let fallback = bank.filter { !used.contains($0.id) }
            let candidates = pool.isEmpty ? fallback : pool
            guard !candidates.isEmpty else { continue }

            let index = Int(generator.next() % UInt64(candidates.count))
            let picked = candidates[index]
            used.insert(picked.id)
            questions.append(QuestionBank.presenting(picked, using: &generator))
        }

        return DailyChallenge(day: day, questions: questions)
    }

    /// Exact pre-v2 implementation. Keep this isolated: standard-library
    /// shuffle/random selection are intentionally allowed only for historical
    /// days whose iOS behavior is already shipped.
    private static func legacyChallenge(
        for day: Int,
        using bank: [TriviaQuestion]
    ) -> DailyChallenge {
        var generator = SeededGenerator(seed: seed(for: day))

        var categories = categoryRoster.shuffled(using: &generator)
        if categories.count > questionCount {
            categories.removeLast(categories.count - questionCount)
        }

        var questions: [TriviaQuestion] = []
        questions.reserveCapacity(questionCount)
        var used: Set<String> = []

        for slot in 0..<questionCount {
            let difficulty = ramp[slot % ramp.count]
            let category = categories[slot % max(categories.count, 1)]
            let pool = bank.filter {
                $0.category == category && $0.difficulty == difficulty && !used.contains($0.id)
            }
            let fallback = bank.filter { !used.contains($0.id) }
            guard let picked = (pool.isEmpty ? fallback : pool).randomElement(using: &generator) else { continue }

            used.insert(picked.id)
            questions.append(QuestionBank.presenting(picked, using: &generator))
        }

        return DailyChallenge(day: day, questions: questions)
    }

    /// Public only so another client can validate the cross-platform contract
    /// without duplicating an undocumented magic formula.
    public static func seed(for day: Int) -> UInt64 {
        let base = UInt64(bitPattern: Int64(day) &+ 0x1_0000)
        return (base &* 0x9E37_79B9_7F4A_7C15) ^ 0x4441_494C_5921_2121
    }
}

/// Consecutive-day counting for the daily challenge.
public enum DailyStreak {
    public static func current(playedDays: Set<Int>, today: Int) -> Int {
        var day = playedDays.contains(today) ? today : today - 1
        guard playedDays.contains(day) else { return 0 }
        var length = 0
        while playedDays.contains(day) {
            length += 1
            day -= 1
        }
        return length
    }

    public static func dayAtRisk(
        playedDays: Set<Int>,
        today: Int,
        minimumStreak: Int = 2
    ) -> (day: Int, streak: Int)? {
        let streak = current(playedDays: playedDays, today: today)
        guard streak >= minimumStreak else { return nil }
        return (playedDays.contains(today) ? today + 1 : today, streak)
    }
}

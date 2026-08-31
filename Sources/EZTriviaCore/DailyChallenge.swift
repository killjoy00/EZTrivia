import Foundation

/// The once-a-day round that every player sees an identical copy of.
///
/// The point of a daily is that it is shared: two people who played today
/// answered the same ten questions, in the same order, with the answers in the
/// same positions. That is what makes a score worth sending to someone, and
/// what makes a daily leaderboard fair. So nothing here is random at runtime —
/// the whole round is derived from the day number and is reproducible on any
/// device, on any build, without a server.
///
/// The day boundary is the player's own local midnight rather than UTC. It
/// means someone in Auckland reaches a given day before someone in Los Angeles,
/// which is the same trade every daily puzzle makes: a day that matches the
/// player's calendar is worth more than a day that is globally simultaneous.
public struct DailyChallenge: Sendable, Equatable {
    /// Questions in a daily round.
    public static let questionCount = 10

    /// The current Daily Challenge roster.
    ///
    /// Explicit rather than `TriviaCategory.allCases`, on purpose: `allCases`
    /// is application state, not a stable wire format, so a category added
    /// for ordinary rounds should not silently reshape today's seeded round
    /// for players before everyone is running the build that knows about it.
    /// Adding Literature and Art here does exactly that for anyone still on
    /// the previous build -- their local build serves the old twelve-category
    /// round while a friend on the new build gets a Books & Literature or Art
    /// & Architecture slot, so a same-day daily leaderboard briefly compares
    /// two different rounds until the old build ages out. Accepted as a
    /// one-time cost of adding categories to the roster; a same-day rollover
    /// has no way to avoid it.
    static let categoryRoster: [TriviaCategory] = [
        .football, .basketball, .soccer, .flags, .history, .science, .movies,
        .tv, .geography, .music, .animals, .food, .literature, .art
    ]

    /// Difficulty ramp for the round, in order.
    ///
    /// Fixed rather than random so every daily has the same shape: an
    /// approachable opening, a middle that requires thought, and two at the end
    /// that decide the leaderboard. A random mix would make some days trivially
    /// easy and others punishing, and players would rightly read that as unfair
    /// rather than as variety.
    static let ramp: [TriviaDifficulty] = [
        .easy, .easy, .easy, .medium, .medium, .medium, .medium, .hard, .hard, .hard
    ]

    /// Identifies the round. Also the number shown to players and used in the
    /// share text, so it starts at 1 rather than 0.
    public let day: Int
    public let questions: [TriviaQuestion]

    public var displayNumber: Int { day + 1 }

    public var totalPoints: Int { questions.reduce(0) { $0 + Scoring.points(for: $1) } }

    // MARK: - Day numbering

    /// The first day of the daily challenge. Day numbering counts from here.
    private static let epoch = DateComponents(year: 2026, month: 1, day: 1)

    /// The day number containing `date` in `calendar`'s time zone.
    public static func day(for date: Date, in calendar: Calendar = .current) -> Int {
        guard let epochDate = calendar.date(from: epoch) else { return 0 }
        let from = calendar.startOfDay(for: epochDate)
        let to = calendar.startOfDay(for: date)
        return calendar.dateComponents([.day], from: from, to: to).day ?? 0
    }

    /// Local midnight that opens `day`. The inverse of `day(for:in:)`.
    ///
    /// Added by date arithmetic rather than by multiplying out seconds, so a
    /// day that is 23 or 25 hours long across a daylight-saving change still
    /// starts where the calendar says it does.
    public static func startOfDay(_ day: Int, in calendar: Calendar = .current) -> Date? {
        guard let epochDate = calendar.date(from: epoch) else { return nil }
        return calendar.date(byAdding: .day, value: day, to: calendar.startOfDay(for: epochDate))
    }

    // MARK: - Building

    public static func today(in calendar: Calendar = .current, using bank: [TriviaQuestion] = QuestionBank.all) -> DailyChallenge {
        challenge(for: day(for: Date(), in: calendar), using: bank)
    }

    /// Builds the round for a given day number.
    ///
    /// Every choice below is driven by one seeded generator: which categories
    /// appear, which question fills each slot, and the order of the answers.
    /// Answer order in particular has to be seeded rather than shuffled at
    /// presentation time — if two players saw the options in different orders,
    /// a shared screenshot would be misleading.
    public static func challenge(for day: Int, using bank: [TriviaQuestion] = QuestionBank.all) -> DailyChallenge {
        var generator = SeededGenerator(seed: seed(for: day))

        // Categories are drawn without replacement, so a single daily never
        // asks two questions from the same category. The roster is explicit
        // because `allCases` is application state, not a stable wire format.
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
            // A category with nothing left at this difficulty falls back to the
            // whole bank rather than dropping the slot, so a short round is
            // never served even if the catalog changes shape later.
            let fallback = bank.filter { !used.contains($0.id) }
            guard let picked = (pool.isEmpty ? fallback : pool).randomElement(using: &generator) else { continue }

            used.insert(picked.id)
            // Seeded, so a flag question's redrawn options are the same options
            // every other player sees today.
            questions.append(QuestionBank.presenting(picked, using: &generator))
        }

        return DailyChallenge(day: day, questions: questions)
    }

    /// Spreads consecutive day numbers apart before they reach the generator.
    ///
    /// Feeding 1, 2, 3 straight into an xorshift produces visibly related
    /// streams, which would show up as similar categories on consecutive days.
    /// Multiplying by the golden-ratio constant first decorrelates them.
    private static func seed(for day: Int) -> UInt64 {
        let base = UInt64(bitPattern: Int64(day) &+ 0x1_0000)
        return (base &* 0x9E37_79B9_7F4A_7C15) ^ 0x4441_494C_5921_2121
    }
}

/// Consecutive-day counting for the daily challenge.
public enum DailyStreak {
    /// The run of consecutive days ending today, or ending yesterday if today
    /// has not been played yet.
    ///
    /// Not breaking the streak until a day has been fully missed is the whole
    /// point: a player who opens the app on Tuesday morning should still see
    /// Monday's streak intact and have all of Tuesday to keep it. Requiring
    /// today to be played would show every returning player a zero and tell
    /// them the thing they were maintaining is already gone.
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

    /// The next day on which an unplayed daily would end the current streak,
    /// and how many days are riding on it.
    ///
    /// This is what a reminder is scheduled against. `current` deliberately
    /// keeps yesterday's streak alive through today, which means the day a
    /// streak is actually *at risk* is the first unplayed one: today if today
    /// is still open, otherwise tomorrow. Miss that day entirely and the run
    /// is gone at the next local midnight.
    ///
    /// Returns nil below `minimumStreak`, because a one-day run is not yet a
    /// streak worth interrupting someone's evening over.
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

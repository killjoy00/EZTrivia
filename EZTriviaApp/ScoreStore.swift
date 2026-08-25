import Foundation

@MainActor
final class ScoreStore: ObservableObject {
    @Published private(set) var entries: [LeaderboardEntry] = []
    private let defaults: UserDefaults
    private let key = "leaderboard.v1"
    private let seenDefaultsKey = "seenQuestions.v1"
    private let dailyDefaultsKey = "dailyResults.v1"

    /// Finished daily challenges, keyed by day number.
    ///
    /// Keeping the whole history rather than just the streak length means the
    /// streak can be recomputed rather than incremented, so a clock change or
    /// a skipped launch cannot leave a counter that disagrees with what the
    /// player actually played.
    @Published private(set) var dailyResults: [Int: DailyResult] = [:]

    /// Question IDs the player has already been served, keyed by
    /// "category-difficulty". Persisting these is what stops a player from
    /// being handed the same ten questions every time they open a category.
    @Published private(set) var seenQuestionIDs: [String: Set<String>] = [:]

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let data = defaults.data(forKey: key), let decoded = try? JSONDecoder().decode([LeaderboardEntry].self, from: data) {
            entries = decoded
        }
        if let data = defaults.data(forKey: seenDefaultsKey), let decoded = try? JSONDecoder().decode([String: Set<String>].self, from: data) {
            seenQuestionIDs = decoded
        }
        if let data = defaults.data(forKey: dailyDefaultsKey), let decoded = try? JSONDecoder().decode([Int: DailyResult].self, from: data) {
            dailyResults = decoded
        }
    }

    // MARK: - Daily challenge

    func dailyResult(for day: Int) -> DailyResult? { dailyResults[day] }

    /// The streak as of `today`. See `DailyStreak.current`.
    func dailyStreak(today: Int) -> Int {
        DailyStreak.current(playedDays: Set(dailyResults.keys), today: today)
    }

    /// Records a finished daily. The first result for a day wins: a daily is
    /// one attempt, so a replay must not be able to overwrite a worse score
    /// with a better one.
    func recordDaily(_ result: DailyResult) {
        guard dailyResults[result.day] == nil else { return }
        dailyResults[result.day] = result
        persistDaily()
    }

    private func persistDaily() {
        defaults.set(try? JSONEncoder().encode(dailyResults), forKey: dailyDefaultsKey)
    }

    private func cacheKey(_ category: TriviaCategory, _ difficulty: TriviaDifficulty) -> String {
        "\(category.rawValue)-\(difficulty.rawValue)"
    }

    func seenQuestions(category: TriviaCategory, difficulty: TriviaDifficulty) -> Set<String> {
        seenQuestionIDs[cacheKey(category, difficulty)] ?? []
    }

    /// Records the questions just served. Once every question in a tier has been
    /// seen the set is cleared, so the player starts a fresh cycle instead of
    /// being stuck with a permanently exhausted pool.
    func markSeen(_ ids: Set<String>, category: TriviaCategory, difficulty: TriviaDifficulty) {
        let key = cacheKey(category, difficulty)
        var updated = (seenQuestionIDs[key] ?? []).union(ids)
        if updated.count >= QuestionPicker.availableCount(category: category, difficulty: difficulty) {
            updated = ids
        }
        seenQuestionIDs[key] = updated
        defaults.set(try? JSONEncoder().encode(seenQuestionIDs), forKey: seenDefaultsKey)
    }

    func record(category: TriviaCategory, difficulty: TriviaDifficulty, score: Int, total: Int) {
        entries.append(LeaderboardEntry(category: category, difficulty: difficulty, score: score, total: total))
        entries = Array(entries.sorted { lhs, rhs in
            lhs.percentage == rhs.percentage ? lhs.date > rhs.date : lhs.percentage > rhs.percentage
        }.prefix(50))
        persist()
    }

    func bestScore(for category: TriviaCategory, difficulty: TriviaDifficulty) -> Int? {
        entries
            .filter { $0.category == category && $0.difficulty == difficulty }
            .map(\.percentage)
            .max()
    }

    /// Clears the personal leaderboard and the seen-question history.
    ///
    /// Daily results are deliberately not cleared. "Clear scores" is about the
    /// local score list; wiping a streak someone has spent weeks on because
    /// they tidied their leaderboard would be a genuinely upsetting surprise.
    func clear() {
        entries = []
        seenQuestionIDs = [:]
        defaults.removeObject(forKey: seenDefaultsKey)
        persist()
    }

    private func persist() {
        defaults.set(try? JSONEncoder().encode(entries), forKey: key)
    }
}

/// One finished daily challenge.
struct DailyResult: Codable, Equatable {
    let day: Int
    let score: Int
    let total: Int
    let points: Int
    let outcomes: [Bool]
    let date: Date

    init(day: Int, score: Int, total: Int, points: Int, outcomes: [Bool], date: Date = Date()) {
        self.day = day
        self.score = score
        self.total = total
        self.points = points
        self.outcomes = outcomes
        self.date = date
    }
}

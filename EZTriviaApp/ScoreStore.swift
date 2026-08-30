import Foundation

@MainActor
final class ScoreStore: ObservableObject {
    @Published private(set) var entries: [LeaderboardEntry] = []
    private let defaults: UserDefaults
    private let key = "leaderboard.v1"
    private let seenDefaultsKey = "seenQuestions.v1"
    private let dailyDefaultsKey = "dailyResults.v1"
    private let lifetimeDefaultsKey = "lifetimePoints.v1"
    private let friendDefaultsKey = "friendChallengeResults.v1"
    private let totalRoundsDefaultsKey = "achievement.totalRounds.v1"
    private let playedCategoriesDefaultsKey = "achievement.playedCategories.v1"
    private let perfectDifficultiesDefaultsKey = "achievement.perfectDifficulties.v1"

    /// Finished daily challenges, keyed by day number.
    ///
    /// Keeping the whole history rather than just the streak length means the
    /// streak can be recomputed rather than incremented, so a clock change or
    /// a skipped launch cannot leave a counter that disagrees with what the
    /// player actually played.
    @Published private(set) var dailyResults: [Int: DailyResult] = [:]

    /// Completed friend challenges, keyed by the version and seed rather than
    /// by the full code. Changing the claimed target score cannot therefore be
    /// used to replay the same question set after the one allowed attempt.
    @Published private(set) var friendResultsByAttemptID: [String: FriendChallengeResult] = [:]

    /// Difficulty-weighted points earned per category, for all time.
    ///
    /// This is what the category leaderboards report, in place of a per-round
    /// percentage. A percentage saturates -- a great many players reach 100 on
    /// a ten-question round, and a board whose top entries are all identical
    /// has stopped ranking anyone. A lifetime total only ever grows, so it
    /// keeps separating players indefinitely.
    ///
    /// Keyed by raw value rather than by TriviaCategory so the stored JSON
    /// survives a category being added, renamed in the UI, or reordered.
    @Published private(set) var lifetimePointsByCategory: [String: Int] = [:]

    /// Question IDs the player has already been served, keyed by
    /// "category-difficulty". Persisting these is what stops a player from
    /// being handed the same ten questions every time they open a category.
    @Published private(set) var seenQuestionIDs: [String: Set<String>] = [:]

    /// Monotonic local facts used by achievements. They are stored separately
    /// from the 50-entry recent-round list so a player can still earn the 100
    /// round achievement after older rows roll out of that display history.
    @Published private(set) var totalRoundsCompleted = 0
    @Published private(set) var playedCategoryRawValues: Set<String> = []
    @Published private(set) var perfectDifficultyRawValues: Set<String> = []

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
        if let data = defaults.data(forKey: lifetimeDefaultsKey), let decoded = try? JSONDecoder().decode([String: Int].self, from: data) {
            lifetimePointsByCategory = decoded
        }
        if let data = defaults.data(forKey: friendDefaultsKey), let decoded = try? JSONDecoder().decode([String: FriendChallengeResult].self, from: data) {
            friendResultsByAttemptID = decoded
        }

        migrateAchievementFactsIfNeeded()
    }

    // MARK: - Lifetime points

    func lifetimePoints(for category: TriviaCategory) -> Int {
        lifetimePointsByCategory[category.rawValue] ?? 0
    }

    var lifetimePointsTotal: Int { lifetimePointsByCategory.values.reduce(0, +) }

    /// Adds a round's points to a category's lifetime total and returns the new
    /// total, which is what gets submitted to Game Center.
    @discardableResult
    func addLifetimePoints(_ points: Int, category: TriviaCategory) -> Int {
        let updated = lifetimePoints(for: category) + points
        lifetimePointsByCategory[category.rawValue] = updated
        defaults.set(try? JSONEncoder().encode(lifetimePointsByCategory), forKey: lifetimeDefaultsKey)
        return updated
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
    func recordDaily(_ result: DailyResult, categories: Set<TriviaCategory> = []) {
        guard dailyResults[result.day] == nil else { return }
        dailyResults[result.day] = result
        persistDaily()
        recordCompletedRound(categories: categories)
    }

    private func persistDaily() {
        defaults.set(try? JSONEncoder().encode(dailyResults), forKey: dailyDefaultsKey)
    }

    // MARK: - Friend challenges

    func friendResult(for code: FriendChallengeCode) -> FriendChallengeResult? {
        friendResultsByAttemptID[code.attemptID]
    }

    func friendResult(forAttemptID attemptID: String) -> FriendChallengeResult? {
        friendResultsByAttemptID[attemptID]
    }

    func recordFriendChallenge(
        _ result: FriendChallengeResult,
        categories: Set<TriviaCategory>
    ) {
        guard friendResultsByAttemptID[result.code.attemptID] == nil else { return }
        friendResultsByAttemptID[result.code.attemptID] = result
        defaults.set(try? JSONEncoder().encode(friendResultsByAttemptID), forKey: friendDefaultsKey)
        recordCompletedRound(categories: categories)
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

    /// Records a finished round, newest first.
    ///
    /// Ordered by date rather than by score. Ranking ten-question rounds
    /// against each other has the same saturation problem the Game Center
    /// boards had before they moved to lifetime points: perfect rounds are
    /// common, so a score-ranked list becomes a wall of 10/10 with the oldest
    /// one arbitrarily on top. As a history it is useful; as a ranking it
    /// stopped saying anything. Lifetime points is where ranking lives now.
    func record(category: TriviaCategory, difficulty: TriviaDifficulty, score: Int, total: Int) {
        entries.append(LeaderboardEntry(category: category, difficulty: difficulty, score: score, total: total))
        entries = Array(entries.sorted { $0.date > $1.date }.prefix(50))
        persist()
        recordCompletedRound(
            categories: [category],
            perfectDifficulty: score == total && total > 0 ? difficulty : nil
        )
    }

    func bestScore(for category: TriviaCategory, difficulty: TriviaDifficulty) -> Int? {
        entries
            .filter { $0.category == category && $0.difficulty == difficulty }
            .map(\.percentage)
            .max()
    }

    /// Clears the personal leaderboard and the seen-question history.
    ///
    /// Daily results and lifetime points are deliberately not cleared. "Clear
    /// scores" is about the local score list. Wiping a streak someone spent
    /// weeks on would be an upsetting surprise, and resetting lifetime points
    /// would be worse than useless: Game Center keeps the high-water mark, so
    /// the device would start counting from zero and the two would disagree
    /// permanently.
    func clear() {
        entries = []
        seenQuestionIDs = [:]
        defaults.removeObject(forKey: seenDefaultsKey)
        persist()
    }

    private func persist() {
        defaults.set(try? JSONEncoder().encode(entries), forKey: key)
    }

    // MARK: - Achievement facts

    var playedCategories: Set<TriviaCategory> {
        Set(playedCategoryRawValues.compactMap(TriviaCategory.init(rawValue:)))
    }

    var perfectDifficulties: Set<TriviaDifficulty> {
        Set(perfectDifficultyRawValues.compactMap(TriviaDifficulty.init(rawValue:)))
    }

    var longestDailyStreak: Int {
        let days = dailyResults.keys.sorted()
        guard !days.isEmpty else { return 0 }
        var longest = 1
        var current = 1
        for index in days.indices.dropFirst() {
            if days[index] == days[index - 1] + 1 {
                current += 1
                longest = max(longest, current)
            } else {
                current = 1
            }
        }
        return longest
    }

    private func recordCompletedRound(
        categories: Set<TriviaCategory>,
        perfectDifficulty: TriviaDifficulty? = nil
    ) {
        totalRoundsCompleted += 1
        playedCategoryRawValues.formUnion(categories.map(\.rawValue))
        if let perfectDifficulty {
            perfectDifficultyRawValues.insert(perfectDifficulty.rawValue)
        }
        persistAchievementFacts()
    }

    /// Existing installations predate explicit achievement counters. Recover
    /// every fact the stored score, daily, lifetime, and friend histories can
    /// prove; never invent progress that cannot be established from local data.
    private func migrateAchievementFactsIfNeeded() {
        if defaults.object(forKey: totalRoundsDefaultsKey) != nil {
            totalRoundsCompleted = defaults.integer(forKey: totalRoundsDefaultsKey)
        } else {
            totalRoundsCompleted = entries.count + dailyResults.count + friendResultsByAttemptID.count
        }

        if let stored = defaults.stringArray(forKey: playedCategoriesDefaultsKey) {
            playedCategoryRawValues = Set(stored)
        } else {
            playedCategoryRawValues = Set(entries.map { $0.category.rawValue })
            playedCategoryRawValues.formUnion(
                lifetimePointsByCategory.filter { $0.value > 0 }.map { $0.key }
            )
            for day in dailyResults.keys {
                playedCategoryRawValues.formUnion(
                    DailyChallenge.challenge(for: day).questions.map { $0.category.rawValue }
                )
            }
            for result in friendResultsByAttemptID.values {
                playedCategoryRawValues.formUnion(
                    FriendChallenge.challenge(for: result.code.seed).questions.map { $0.category.rawValue }
                )
            }
        }

        if let stored = defaults.stringArray(forKey: perfectDifficultiesDefaultsKey) {
            perfectDifficultyRawValues = Set(stored)
        } else {
            perfectDifficultyRawValues = Set(entries.compactMap { entry in
                guard entry.total > 0, entry.score == entry.total else { return nil }
                return entry.difficulty?.rawValue
            })
        }

        persistAchievementFacts()
    }

    private func persistAchievementFacts() {
        defaults.set(totalRoundsCompleted, forKey: totalRoundsDefaultsKey)
        defaults.set(playedCategoryRawValues.sorted(), forKey: playedCategoriesDefaultsKey)
        defaults.set(perfectDifficultyRawValues.sorted(), forKey: perfectDifficultiesDefaultsKey)
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

/// One locally enforced attempt at a friend challenge.
struct FriendChallengeResult: Codable, Equatable {
    let code: FriendChallengeCode
    let score: Int
    let total: Int
    let points: Int
    let outcomes: [Bool]
    let createdChallenge: Bool
    let date: Date

    init(
        code: FriendChallengeCode,
        score: Int,
        total: Int,
        points: Int,
        outcomes: [Bool],
        createdChallenge: Bool,
        date: Date = Date()
    ) {
        self.code = code
        self.score = score
        self.total = total
        self.points = points
        self.outcomes = outcomes
        self.createdChallenge = createdChallenge
        self.date = date
    }
}

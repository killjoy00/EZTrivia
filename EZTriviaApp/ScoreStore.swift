import Foundation

@MainActor
final class ScoreStore: ObservableObject {
    @Published private(set) var entries: [LeaderboardEntry] = []
    private let defaults: UserDefaults
    private let cloudStore: NSUbiquitousKeyValueStore

    private let key = "leaderboard.v1"
    private let seenDefaultsKey = "seenQuestions.v1"
    private let dailyDefaultsKey = "dailyResults.v1"
    private let lifetimeDefaultsKey = "lifetimePoints.v1"
    private let lifetimeBaseDefaultsKey = "lifetimePoints.base.v2"
    private let lifetimeDeviceDefaultsKey = "lifetimePoints.byDevice.v2"
    private let installationIDDefaultsKey = "player.installationID.v1"
    private let friendDefaultsKey = "friendChallengeResults.v1"
    private let quickPlayDefaultsKey = "quickPlayResults.v1"
    private let quickPlayRoundsDefaultsKey = "achievement.quickPlayRounds.v1"
    private let totalRoundsDefaultsKey = "achievement.totalRounds.v1"
    private let playedCategoriesDefaultsKey = "achievement.playedCategories.v1"
    private let perfectDifficultiesDefaultsKey = "achievement.perfectDifficulties.v1"
    private let recentResetDefaultsKey = "leaderboard.resetAt.v1"
    private let seenResetDefaultsKey = "seenQuestions.resetAt.v1"
    private let cloudModifiedDefaultsKey = "icloud.playerState.modifiedAt.v1"
    private let cloudStateKey = "playerState.v1"

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

    /// Recent Quick Play rounds. Quick Play is deliberately not a Game Center
    /// category leaderboard because a mixed round does not belong to one
    /// category; this history still makes the mode visible in Scores.
    @Published private(set) var quickPlayResults: [QuickPlayResult] = []

    /// Difficulty-weighted points earned per category, for all time.
    ///
    /// This is what the category leaderboards report, in place of a per-round
    /// percentage. A percentage saturates -- a great many players reach 100 on
    /// a ten-question round, and a board whose top entries are all identical
    /// has stopped ranking anyone. A lifetime total only ever grows, so it
    /// keeps separating players indefinitely.
    ///
    /// The public dictionary is a derived cache. v2 stores a shared migration
    /// baseline plus per-installation increments, which lets iCloud merge new
    /// play from two devices additively instead of choosing whichever device
    /// happened to have the larger total.
    @Published private(set) var lifetimePointsByCategory: [String: Int] = [:]
    private var lifetimeBaseByCategory: [String: Int] = [:]
    private var lifetimeIncrementsByDevice: [String: [String: Int]] = [:]
    private let installationID: String

    /// Question IDs the player has already been served, keyed by
    /// "category-difficulty". Persisting these is what stops a player from
    /// being handed the same ten questions every time they open a category.
    @Published private(set) var seenQuestionIDs: [String: Set<String>] = [:]

    /// Monotonic local facts used by achievements. They are stored separately
    /// from the 50-entry recent-round list so a player can still earn the 100
    /// round achievement after older rows roll out of that display history.
    @Published private(set) var totalRoundsCompleted = 0
    @Published private(set) var quickPlayRoundsCompleted = 0
    @Published private(set) var playedCategoryRawValues: Set<String> = []
    @Published private(set) var perfectDifficultyRawValues: Set<String> = []

    private var recentHistoryResetAt = Date.distantPast
    private var seenQuestionsResetAt = Date.distantPast
    private var cloudModifiedAt = Date.distantPast
    private var isApplyingCloudState = false
    private var cloudObserver: NSObjectProtocol?

    init(
        defaults: UserDefaults = .standard,
        cloudStore: NSUbiquitousKeyValueStore = .default
    ) {
        self.defaults = defaults
        self.cloudStore = cloudStore
        if let storedInstallationID = defaults.string(forKey: installationIDDefaultsKey) {
            installationID = storedInstallationID
        } else {
            let newID = UUID().uuidString
            defaults.set(newID, forKey: installationIDDefaultsKey)
            installationID = newID
        }

        if let data = defaults.data(forKey: key),
           let decoded = try? JSONDecoder().decode([LeaderboardEntry].self, from: data) {
            entries = decoded
        }
        if let data = defaults.data(forKey: seenDefaultsKey),
           let decoded = try? JSONDecoder().decode([String: Set<String>].self, from: data) {
            seenQuestionIDs = decoded
        }
        if let data = defaults.data(forKey: dailyDefaultsKey),
           let decoded = try? JSONDecoder().decode([Int: DailyResult].self, from: data) {
            dailyResults = decoded
        }
        if let data = defaults.data(forKey: lifetimeDefaultsKey),
           let decoded = try? JSONDecoder().decode([String: Int].self, from: data) {
            lifetimePointsByCategory = decoded
        }
        if let data = defaults.data(forKey: lifetimeBaseDefaultsKey),
           let decoded = try? JSONDecoder().decode([String: Int].self, from: data) {
            lifetimeBaseByCategory = decoded
        } else {
            // One shared migration baseline prevents two upgraded devices that
            // both already know the same historical total from double-counting
            // it when their iCloud snapshots meet.
            lifetimeBaseByCategory = lifetimePointsByCategory
        }
        if let data = defaults.data(forKey: lifetimeDeviceDefaultsKey),
           let decoded = try? JSONDecoder().decode([String: [String: Int]].self, from: data) {
            lifetimeIncrementsByDevice = decoded
        }
        rebuildLifetimePoints()

        if let data = defaults.data(forKey: friendDefaultsKey),
           let decoded = try? JSONDecoder().decode([String: FriendChallengeResult].self, from: data) {
            friendResultsByAttemptID = decoded
        }
        if let data = defaults.data(forKey: quickPlayDefaultsKey),
           let decoded = try? JSONDecoder().decode([QuickPlayResult].self, from: data) {
            quickPlayResults = decoded
        }

        recentHistoryResetAt = defaults.object(forKey: recentResetDefaultsKey) as? Date ?? .distantPast
        seenQuestionsResetAt = defaults.object(forKey: seenResetDefaultsKey) as? Date ?? .distantPast
        cloudModifiedAt = defaults.object(forKey: cloudModifiedDefaultsKey) as? Date ?? .distantPast

        migrateAchievementFactsIfNeeded()
        persistLifetimeComponents()
        startCloudSync()
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
        var deviceTotals = lifetimeIncrementsByDevice[installationID] ?? [:]
        deviceTotals[category.rawValue, default: 0] += points
        lifetimeIncrementsByDevice[installationID] = deviceTotals
        rebuildLifetimePoints()
        persistLifetimeComponents()
        noteLocalMutation()
        return lifetimePoints(for: category)
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
        noteLocalMutation()
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
        noteLocalMutation()
    }

    // MARK: - Quick Play

    func recordQuickPlay(_ result: QuickPlayResult, categories: Set<TriviaCategory>) {
        guard !quickPlayResults.contains(where: { $0.id == result.id }) else { return }
        quickPlayResults.append(result)
        quickPlayResults = Array(quickPlayResults.sorted { $0.date > $1.date }.prefix(20))
        quickPlayRoundsCompleted += 1
        defaults.set(quickPlayRoundsCompleted, forKey: quickPlayRoundsDefaultsKey)
        defaults.set(try? JSONEncoder().encode(quickPlayResults), forKey: quickPlayDefaultsKey)
        recordCompletedRound(categories: categories)
        noteLocalMutation()
    }

    // MARK: - Seen questions

    private func cacheKey(_ category: TriviaCategory, _ difficulty: TriviaDifficulty) -> String {
        "\(category.rawValue)-\(difficulty.rawValue)"
    }

    var allSeenQuestionIDs: Set<String> {
        seenQuestionIDs.values.reduce(into: Set<String>()) { partial, ids in
            partial.formUnion(ids)
        }
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
        noteLocalMutation()
    }

    /// Records a finished category round, newest first.
    func record(category: TriviaCategory, difficulty: TriviaDifficulty, score: Int, total: Int) {
        entries.append(LeaderboardEntry(category: category, difficulty: difficulty, score: score, total: total))
        entries = Array(entries.sorted { $0.date > $1.date }.prefix(50))
        persist()
        recordCompletedRound(
            categories: [category],
            perfectDifficulty: score == total && total > 0 ? difficulty : nil
        )
        noteLocalMutation()
    }

    func bestScore(for category: TriviaCategory, difficulty: TriviaDifficulty) -> Int? {
        entries
            .filter { $0.category == category && $0.difficulty == difficulty }
            .map(\.percentage)
            .max()
    }

    /// Clears recent category rounds and starts a fresh seen-question cycle.
    /// Daily results, Friend Challenges, Quick Play history, achievements, and
    /// lifetime points are deliberately kept. Reset timestamps make the clear
    /// operation propagate through iCloud rather than having an older device
    /// resurrect the rows on the next merge.
    func clear() {
        let now = Date()
        entries = []
        seenQuestionIDs = [:]
        recentHistoryResetAt = now
        seenQuestionsResetAt = now
        defaults.set(now, forKey: recentResetDefaultsKey)
        defaults.set(now, forKey: seenResetDefaultsKey)
        defaults.removeObject(forKey: seenDefaultsKey)
        persist()
        noteLocalMutation()
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

    var hasPerfectDaily: Bool {
        dailyResults.values.contains { $0.total > 0 && $0.score == $0.total }
    }

    var friendChallengesCompleted: Int { friendResultsByAttemptID.count }

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
            totalRoundsCompleted = entries.count + dailyResults.count + friendResultsByAttemptID.count + quickPlayResults.count
        }

        if defaults.object(forKey: quickPlayRoundsDefaultsKey) != nil {
            quickPlayRoundsCompleted = defaults.integer(forKey: quickPlayRoundsDefaultsKey)
        } else {
            quickPlayRoundsCompleted = quickPlayResults.count
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
        defaults.set(quickPlayRoundsCompleted, forKey: quickPlayRoundsDefaultsKey)
        defaults.set(playedCategoryRawValues.sorted(), forKey: playedCategoriesDefaultsKey)
        defaults.set(perfectDifficultyRawValues.sorted(), forKey: perfectDifficultiesDefaultsKey)
    }

    // MARK: - iCloud state sync

    /// iCloud key-value storage is intentionally used instead of a bespoke
    /// backend: this is private player state, not a social database. The merge
    /// rules are conservative: one-attempt records keep the earliest result,
    /// monotonic achievement facts never move backward, and category lifetime
    /// points merge per installation so play on two devices adds together.
    private struct CloudSnapshot: Codable, Equatable {
        let schemaVersion: Int
        let modifiedAt: Date
        let entries: [LeaderboardEntry]
        let seenQuestionIDs: [String: Set<String>]
        let dailyResults: [Int: DailyResult]
        let lifetimeBaseByCategory: [String: Int]
        let lifetimeIncrementsByDevice: [String: [String: Int]]
        let friendResultsByAttemptID: [String: FriendChallengeResult]
        let quickPlayResults: [QuickPlayResult]
        let totalRoundsCompleted: Int
        let quickPlayRoundsCompleted: Int
        let playedCategoryRawValues: Set<String>
        let perfectDifficultyRawValues: Set<String>
        let recentHistoryResetAt: Date
        let seenQuestionsResetAt: Date
    }

    private func startCloudSync() {
        cloudObserver = NotificationCenter.default.addObserver(
            forName: NSUbiquitousKeyValueStore.didChangeExternallyNotification,
            object: cloudStore,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.mergeCloudStateIfAvailable()
            }
        }

        cloudStore.synchronize()
        if cloudStore.data(forKey: cloudStateKey) != nil {
            mergeCloudStateIfAvailable()
        } else {
            noteLocalMutation()
        }
    }

    private func noteLocalMutation() {
        guard !isApplyingCloudState else { return }
        cloudModifiedAt = Date()
        defaults.set(cloudModifiedAt, forKey: cloudModifiedDefaultsKey)
        publishCloudState()
    }

    private func publishCloudState() {
        guard !isApplyingCloudState,
              let data = try? JSONEncoder().encode(snapshot(modifiedAt: cloudModifiedAt)) else { return }
        cloudStore.set(data, forKey: cloudStateKey)
        cloudStore.synchronize()
    }

    private func mergeCloudStateIfAvailable() {
        guard let data = cloudStore.data(forKey: cloudStateKey),
              let remote = try? JSONDecoder().decode(CloudSnapshot.self, from: data),
              remote.schemaVersion == 1 else { return }

        isApplyingCloudState = true
        defer { isApplyingCloudState = false }

        let localModifiedAt = cloudModifiedAt
        let mergedRecentReset = max(recentHistoryResetAt, remote.recentHistoryResetAt)
        let mergedSeenReset = max(seenQuestionsResetAt, remote.seenQuestionsResetAt)

        var entriesByID = Dictionary(uniqueKeysWithValues: entries.map { ($0.id, $0) })
        for entry in remote.entries {
            entriesByID[entry.id] = entry
        }
        entries = Array(entriesByID.values
            .filter { $0.date > mergedRecentReset }
            .sorted { $0.date > $1.date }
            .prefix(50))
        recentHistoryResetAt = mergedRecentReset

        if remote.seenQuestionsResetAt > seenQuestionsResetAt {
            seenQuestionIDs = remote.seenQuestionIDs
        } else if remote.seenQuestionsResetAt == seenQuestionsResetAt {
            for (key, ids) in remote.seenQuestionIDs {
                seenQuestionIDs[key, default: []].formUnion(ids)
            }
        }
        seenQuestionsResetAt = mergedSeenReset

        for (day, remoteResult) in remote.dailyResults {
            if let localResult = dailyResults[day] {
                dailyResults[day] = localResult.date <= remoteResult.date ? localResult : remoteResult
            } else {
                dailyResults[day] = remoteResult
            }
        }

        for (category, remoteBase) in remote.lifetimeBaseByCategory {
            lifetimeBaseByCategory[category] = max(lifetimeBaseByCategory[category] ?? 0, remoteBase)
        }
        for (device, remoteTotals) in remote.lifetimeIncrementsByDevice {
            var localTotals = lifetimeIncrementsByDevice[device] ?? [:]
            for (category, points) in remoteTotals {
                localTotals[category] = max(localTotals[category] ?? 0, points)
            }
            lifetimeIncrementsByDevice[device] = localTotals
        }
        rebuildLifetimePoints()

        for (attemptID, remoteResult) in remote.friendResultsByAttemptID {
            if let localResult = friendResultsByAttemptID[attemptID] {
                friendResultsByAttemptID[attemptID] = localResult.date <= remoteResult.date ? localResult : remoteResult
            } else {
                friendResultsByAttemptID[attemptID] = remoteResult
            }
        }

        var quickByID = Dictionary(uniqueKeysWithValues: quickPlayResults.map { ($0.id, $0) })
        for result in remote.quickPlayResults {
            quickByID[result.id] = result
        }
        quickPlayResults = Array(quickByID.values.sorted { $0.date > $1.date }.prefix(20))

        totalRoundsCompleted = max(totalRoundsCompleted, remote.totalRoundsCompleted)
        quickPlayRoundsCompleted = max(quickPlayRoundsCompleted, remote.quickPlayRoundsCompleted)
        playedCategoryRawValues.formUnion(remote.playedCategoryRawValues)
        perfectDifficultyRawValues.formUnion(remote.perfectDifficultyRawValues)

        persistAllLocalState()

        let stateAtRemoteTimestamp = snapshot(modifiedAt: remote.modifiedAt)
        if stateAtRemoteTimestamp == remote && remote.modifiedAt >= localModifiedAt {
            cloudModifiedAt = remote.modifiedAt
            defaults.set(cloudModifiedAt, forKey: cloudModifiedDefaultsKey)
        } else {
            cloudModifiedAt = Date()
            defaults.set(cloudModifiedAt, forKey: cloudModifiedDefaultsKey)
            isApplyingCloudState = false
            publishCloudState()
            isApplyingCloudState = true
        }
    }

    private func snapshot(modifiedAt: Date) -> CloudSnapshot {
        CloudSnapshot(
            schemaVersion: 1,
            modifiedAt: modifiedAt,
            entries: entries,
            seenQuestionIDs: seenQuestionIDs,
            dailyResults: dailyResults,
            lifetimeBaseByCategory: lifetimeBaseByCategory,
            lifetimeIncrementsByDevice: lifetimeIncrementsByDevice,
            friendResultsByAttemptID: friendResultsByAttemptID,
            quickPlayResults: quickPlayResults,
            totalRoundsCompleted: totalRoundsCompleted,
            quickPlayRoundsCompleted: quickPlayRoundsCompleted,
            playedCategoryRawValues: playedCategoryRawValues,
            perfectDifficultyRawValues: perfectDifficultyRawValues,
            recentHistoryResetAt: recentHistoryResetAt,
            seenQuestionsResetAt: seenQuestionsResetAt
        )
    }

    private func persistAllLocalState() {
        persist()
        defaults.set(try? JSONEncoder().encode(seenQuestionIDs), forKey: seenDefaultsKey)
        persistDaily()
        persistLifetimeComponents()
        defaults.set(try? JSONEncoder().encode(friendResultsByAttemptID), forKey: friendDefaultsKey)
        defaults.set(try? JSONEncoder().encode(quickPlayResults), forKey: quickPlayDefaultsKey)
        defaults.set(recentHistoryResetAt, forKey: recentResetDefaultsKey)
        defaults.set(seenQuestionsResetAt, forKey: seenResetDefaultsKey)
        persistAchievementFacts()
    }

    private func rebuildLifetimePoints() {
        var totals = lifetimeBaseByCategory
        for deviceTotals in lifetimeIncrementsByDevice.values {
            for (category, points) in deviceTotals {
                totals[category, default: 0] += points
            }
        }
        lifetimePointsByCategory = totals
    }

    private func persistLifetimeComponents() {
        defaults.set(try? JSONEncoder().encode(lifetimeBaseByCategory), forKey: lifetimeBaseDefaultsKey)
        defaults.set(try? JSONEncoder().encode(lifetimeIncrementsByDevice), forKey: lifetimeDeviceDefaultsKey)
        defaults.set(try? JSONEncoder().encode(lifetimePointsByCategory), forKey: lifetimeDefaultsKey)
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

/// One completed mixed-category Quick Play round.
struct QuickPlayResult: Identifiable, Codable, Equatable {
    let id: UUID
    let score: Int
    let total: Int
    let points: Int
    let outcomes: [Bool]
    let date: Date

    init(
        id: UUID = UUID(),
        score: Int,
        total: Int,
        points: Int,
        outcomes: [Bool],
        date: Date = Date()
    ) {
        self.id = id
        self.score = score
        self.total = total
        self.points = points
        self.outcomes = outcomes
        self.date = date
    }
}

import Foundation

@MainActor
final class ScoreStore: ObservableObject {
    @Published private(set) var entries: [LeaderboardEntry] = []
    private let defaults: UserDefaults
    private let key = "leaderboard.v1"
    private let seenDefaultsKey = "seenQuestions.v1"

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

import Foundation

@MainActor
final class ScoreStore: ObservableObject {
    @Published private(set) var entries: [LeaderboardEntry] = []
    private let defaults: UserDefaults
    private let key = "leaderboard.v1"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let data = defaults.data(forKey: key), let decoded = try? JSONDecoder().decode([LeaderboardEntry].self, from: data) {
            entries = decoded
        }
    }

    func record(category: TriviaCategory, score: Int, total: Int) {
        entries.append(LeaderboardEntry(category: category, score: score, total: total))
        entries = Array(entries.sorted { lhs, rhs in
            lhs.percentage == rhs.percentage ? lhs.date > rhs.date : lhs.percentage > rhs.percentage
        }.prefix(50))
        persist()
    }

    func bestScore(for category: TriviaCategory) -> Int? {
        entries.filter { $0.category == category && $0.total == 10 }.map(\.score).max()
    }

    func clear() {
        entries = []
        persist()
    }

    private func persist() {
        defaults.set(try? JSONEncoder().encode(entries), forKey: key)
    }
}

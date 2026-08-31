import Foundation

public enum TriviaCategory: String, CaseIterable, Codable, Identifiable, Sendable {
    case football, basketball, soccer, flags, history, science, movies, tv, geography, music, animals, food, literature, art

    public var id: String { rawValue }

    public var title: String {
        switch self {
        case .football: "Football"
        case .basketball: "Basketball"
        case .soccer: "Soccer"
        case .flags: "World Flags"
        case .history: "History"
        case .science: "Science"
        case .movies: "Movies"
        case .tv: "TV"
        case .geography: "Geography"
        case .music: "Music"
        case .animals: "Animals"
        case .food: "Food & Drink"
        case .literature: "Books & Literature"
        case .art: "Art & Architecture"
        }
    }

    public var subtitle: String {
        switch self {
        case .football: "Touchdowns & legends"
        case .basketball: "Hoops, teams & legends"
        case .soccer: "The beautiful game"
        case .flags: "Colors around the globe"
        case .history: "People who shaped our world"
        case .science: "Nature, space & discovery"
        case .movies: "Big-screen favorites"
        case .tv: "Small-screen favorites"
        case .geography: "Places near & far"
        case .music: "Artists, songs & sounds"
        case .animals: "Wildlife & nature"
        case .food: "Flavors of the world"
        case .literature: "Books, authors & stories"
        case .art: "Masterpieces & monuments"
        }
    }

    public var symbol: String {
        switch self {
        case .football: "football.fill"
        case .basketball: "basketball.fill"
        case .soccer: "soccerball"
        case .flags: "flag.fill"
        case .history: "building.columns.fill"
        case .science: "atom"
        case .movies: "film.fill"
        case .tv: "tv.fill"
        case .geography: "globe.americas.fill"
        case .music: "music.note"
        case .animals: "pawprint.fill"
        case .food: "fork.knife"
        case .literature: "books.vertical.fill"
        case .art: "paintpalette.fill"
        }
    }
}

public enum TriviaDifficulty: String, CaseIterable, Codable, Identifiable, Sendable {
    case easy, medium, hard

    public var id: String { rawValue }
    public var title: String { rawValue.capitalized }

    public var subtitle: String {
        switch self {
        case .easy: "A friendly warm-up"
        case .medium: "A balanced challenge"
        case .hard: "For trivia experts"
        }
    }

    public var symbol: String {
        switch self {
        case .easy: "star"
        case .medium: "star.leadinghalf.filled"
        case .hard: "star.fill"
        }
    }
}

public struct TriviaQuestion: Identifiable, Codable, Equatable, Sendable {
    public let id: String
    public let category: TriviaCategory
    public let prompt: String
    public let difficulty: TriviaDifficulty
    /// A large, non-interactive visual shown above the prompt (for example, a flag emoji).
    public let visual: String?
    public let answers: [String]
    public let correctAnswerIndex: Int
    public let explanation: String

    public init(id: String, category: TriviaCategory, prompt: String, difficulty: TriviaDifficulty = .easy, visual: String? = nil, answers: [String], correctAnswerIndex: Int, explanation: String) {
        precondition(answers.indices.contains(correctAnswerIndex))
        self.id = id
        self.category = category
        self.prompt = prompt
        self.difficulty = difficulty
        self.visual = visual
        self.answers = answers
        self.correctAnswerIndex = correctAnswerIndex
        self.explanation = explanation
    }

    /// Returns the same question with its answers reordered.
    ///
    /// Answer positions are fixed in the authored data so the review export is
    /// stable, which would otherwise let a repeat player recognise the position
    /// rather than the answer. Shuffling at presentation time keeps the data
    /// reviewable and the game honest.
    ///
    /// Fisher-Yates rather than the standard library's `shuffle(using:)`,
    /// because the daily challenge drives this from a seeded generator and
    /// promises every player an identical round. `shuffle(using:)` only promises
    /// a uniform permutation, not a particular one, so two players on different
    /// OS versions could see the same daily question with its options in
    /// different orders -- which would make a shared screenshot wrong.
    public func shufflingAnswers(using generator: inout some RandomNumberGenerator) -> TriviaQuestion {
        let correct = answers[correctAnswerIndex]
        let shuffled = answers.deterministicallyShuffled(using: &generator)
        guard let index = shuffled.firstIndex(of: correct) else { return self }
        return TriviaQuestion(
            id: id,
            category: category,
            prompt: prompt,
            difficulty: difficulty,
            visual: visual,
            answers: shuffled,
            correctAnswerIndex: index,
            explanation: explanation
        )
    }

    public func shufflingAnswers() -> TriviaQuestion {
        var generator = SystemRandomNumberGenerator()
        return shufflingAnswers(using: &generator)
    }
}

public struct LeaderboardEntry: Identifiable, Codable, Equatable, Sendable {
    public let id: UUID
    public let category: TriviaCategory
    /// Older saved entries predate difficulty-specific scores and decode as nil.
    public let difficulty: TriviaDifficulty?
    public let score: Int
    public let total: Int
    public let date: Date

    public init(id: UUID = UUID(), category: TriviaCategory, difficulty: TriviaDifficulty? = nil, score: Int, total: Int, date: Date = Date()) {
        self.id = id
        self.category = category
        self.difficulty = difficulty
        self.score = score
        self.total = total
        self.date = date
    }

    public var percentage: Int { total == 0 ? 0 : Int((Double(score) / Double(total) * 100).rounded()) }
}

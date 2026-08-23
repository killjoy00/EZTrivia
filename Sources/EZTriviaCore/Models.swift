import Foundation

public enum TriviaCategory: String, CaseIterable, Codable, Identifiable, Sendable {
    case football, basketball, soccer, flags, history, science, movies, geography, music, animals, food

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
        case .geography: "Geography"
        case .music: "Music"
        case .animals: "Animals"
        case .food: "Food & Drink"
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
        case .geography: "Places near & far"
        case .music: "Artists, songs & sounds"
        case .animals: "Wildlife & nature"
        case .food: "Flavors of the world"
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
        case .geography: "globe.americas.fill"
        case .music: "music.note"
        case .animals: "pawprint.fill"
        case .food: "fork.knife"
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
}

public struct LeaderboardEntry: Identifiable, Codable, Equatable, Sendable {
    public let id: UUID
    public let category: TriviaCategory
    public let score: Int
    public let total: Int
    public let date: Date

    public init(id: UUID = UUID(), category: TriviaCategory, score: Int, total: Int, date: Date = Date()) {
        self.id = id
        self.category = category
        self.score = score
        self.total = total
        self.date = date
    }

    public var percentage: Int { total == 0 ? 0 : Int((Double(score) / Double(total) * 100).rounded()) }
}

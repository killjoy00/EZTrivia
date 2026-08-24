import Foundation

public enum QuestionBank {
    /// Every question in the app, assembled from the hand-authored seed files
    /// and the flag catalog. No question text is ever repeated, and no question
    /// appears at more than one difficulty.
    public static let all: [TriviaQuestion] = buildAll()

    /// The prompt shown for every flag question.
    ///
    /// Roughly 40 percent of the catalog is territories and dependencies rather
    /// than sovereign states, so the wording deliberately says "or territory".
    static let flagPrompt = "Which country or territory does this flag represent?"

    private static let seedsByCategory: [(TriviaCategory, CategorySeeds)] = [
        (.football, FootballQuestions.seeds),
        (.basketball, BasketballQuestions.seeds),
        (.soccer, SoccerQuestions.seeds),
        (.history, HistoryQuestions.seeds),
        (.science, ScienceQuestions.seeds),
        (.movies, MoviesQuestions.seeds),
        (.geography, GeographyQuestions.seeds),
        (.music, MusicQuestions.seeds),
        (.animals, AnimalsQuestions.seeds),
        (.food, FoodQuestions.seeds)
    ]

    private static func buildAll() -> [TriviaQuestion] {
        var questions: [TriviaQuestion] = []
        questions.reserveCapacity(1200)

        for (category, seeds) in seedsByCategory {
            for difficulty in TriviaDifficulty.allCases {
                for (offset, seed) in seeds[difficulty].enumerated() {
                    questions.append(
                        TriviaQuestion(
                            id: "\(category.rawValue)-\(difficulty.rawValue)-\(offset + 1)",
                            category: category,
                            prompt: seed.prompt,
                            difficulty: difficulty,
                            visual: seed.visual,
                            answers: seed.answers,
                            correctAnswerIndex: seed.correctAnswerIndex,
                            explanation: seed.explanation
                        )
                    )
                }
            }
        }

        questions.append(contentsOf: flagQuestions())
        return questions
    }

    /// Builds one question per askable flag.
    ///
    /// Distractors are drawn from the same difficulty tier where possible, so an
    /// easy round offers three other well-known flags rather than three obscure
    /// dependencies. Any flag measured as visually confusable with the answer is
    /// excluded from its own options: telling Egypt from Yemen at phone size is a
    /// coin flip, not a question.
    private static func flagQuestions() -> [TriviaQuestion] {
        let catalog = FlagCatalog.askable
        var generator = SeededGenerator(seed: 0x455A_5452_4956_4941)
        var questions: [TriviaQuestion] = []
        questions.reserveCapacity(catalog.count)

        for (offset, entry) in catalog.enumerated() {
            let sameTier = catalog.filter {
                $0.code != entry.code
                    && $0.difficulty == entry.difficulty
                    && !entry.confusable.contains($0.code)
                    && !$0.confusable.contains(entry.code)
            }
            let anyTier = catalog.filter {
                $0.code != entry.code
                    && !entry.confusable.contains($0.code)
                    && !$0.confusable.contains(entry.code)
            }

            var distractors = Array(sameTier.shuffled(using: &generator).prefix(3))
            if distractors.count < 3 {
                let chosen = Set(distractors.map(\.code))
                let filler = anyTier.filter { !chosen.contains($0.code) }
                distractors += filler.shuffled(using: &generator).prefix(3 - distractors.count)
            }

            var options = (distractors + [entry]).map(\.name)
            options.shuffle(using: &generator)
            guard let correctIndex = options.firstIndex(of: entry.name) else { continue }

            questions.append(
                TriviaQuestion(
                    id: "flags-\(entry.difficulty.rawValue)-\(offset + 1)",
                    category: .flags,
                    prompt: flagPrompt,
                    difficulty: entry.difficulty,
                    visual: entry.asset,
                    answers: options,
                    correctAnswerIndex: correctIndex,
                    explanation: "This is the flag of \(entry.name)."
                )
            )
        }

        return questions
    }
}

/// A small deterministic generator so the shipped flag options are identical on
/// every device and every build. Runtime answer shuffling uses the system
/// generator instead, so two plays of the same question are not identical.
struct SeededGenerator: RandomNumberGenerator {
    private var state: UInt64

    init(seed: UInt64) {
        state = seed == 0 ? 0x9E37_79B9_7F4A_7C15 : seed
    }

    mutating func next() -> UInt64 {
        state ^= state << 13
        state ^= state >> 7
        state ^= state << 17
        return state
    }
}

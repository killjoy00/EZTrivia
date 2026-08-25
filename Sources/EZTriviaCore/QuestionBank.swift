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
        questions.reserveCapacity(1500)

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
    /// The options stored here are only a starting point: `presenting` redraws
    /// them before the question is ever shown. What this pass fixes is the
    /// reviewable form of the question -- the row that lands in
    /// QuestionReview.csv -- so a human proofing the bank sees one concrete,
    /// stable set of choices per flag rather than a sample that changes on
    /// every export.
    private static func flagQuestions() -> [TriviaQuestion] {
        var generator = SeededGenerator(seed: 0x455A_5452_4956_4941)
        var questions: [TriviaQuestion] = []
        questions.reserveCapacity(FlagCatalog.askable.count)

        for entry in FlagCatalog.askable {
            questions.append(flagQuestion(for: entry, using: &generator))
        }

        return questions
    }

    /// One flag question with a freshly drawn set of options.
    ///
    /// The id is derived from the flag's own code rather than its position in
    /// the catalog. Positional ids renumbered every question after any entry
    /// that was added or withdrawn, which silently invalidated the "already
    /// seen" set on players' devices and made review notes referring to a row
    /// number point at a different flag after the next edit.
    static func flagQuestion(
        for entry: FlagEntry,
        using generator: inout some RandomNumberGenerator
    ) -> TriviaQuestion {
        let options = FlagCatalog.options(for: entry, using: &generator)
        let names = options.map(\.name)
        let fact = FlagFacts.byCode[entry.code]
            ?? "This country or territory has a distinctive cultural and geographic history."

        return TriviaQuestion(
            id: "flags-\(entry.difficulty.rawValue)-\(entry.code.lowercased())",
            category: .flags,
            prompt: flagPrompt,
            difficulty: entry.difficulty,
            visual: entry.asset,
            answers: names,
            // `options` always contains `entry`, so this index always exists.
            correctAnswerIndex: options.firstIndex { $0.code == entry.code } ?? 0,
            explanation: entry.explanation.map { "\($0) \(fact)" } ?? fact
        )
    }

    /// The form of a question that is actually put in front of a player.
    ///
    /// Every round goes through here. For an ordinary question that means
    /// reordering the authored answers; for a flag question it means drawing a
    /// new set of wrong answers as well, so the same flag asked twice is a
    /// genuinely different question rather than the same four names in a new
    /// order.
    ///
    /// The generator is the caller's, which is what lets the daily challenge
    /// stay identical for everyone while ordinary rounds vary.
    public static func presenting(
        _ question: TriviaQuestion,
        using generator: inout some RandomNumberGenerator
    ) -> TriviaQuestion {
        guard question.category == .flags, let entry = flagEntry(for: question) else {
            return question.shufflingAnswers(using: &generator)
        }
        return flagQuestion(for: entry, using: &generator)
    }

    /// The catalog entry a flag question is asking about.
    ///
    /// Read from the artwork name rather than the id, because the artwork is
    /// what the player is actually looking at: if the two ever disagreed, the
    /// options must match the image on screen, not the label on the record.
    static func flagEntry(for question: TriviaQuestion) -> FlagEntry? {
        guard let asset = question.visual, asset.hasPrefix("flag-") else { return nil }
        return FlagCatalog.byCode[String(asset.dropFirst("flag-".count)).uppercased()]
    }
}

extension Array {
    /// Fisher-Yates driven only by `generator`, rather than the standard
    /// library's shuffle.
    ///
    /// The bundled bank is exported to QuestionReview.csv and diffed in CI, so
    /// authored answer order has to depend on nothing but the seed. Using
    /// `shuffled(using:)` would tie that file to the toolchain's shuffle
    /// implementation and let an unrelated Swift upgrade fail the check.
    func deterministicallyShuffled(using generator: inout some RandomNumberGenerator) -> [Element] {
        var result = self
        guard result.count > 1 else { return result }

        for index in stride(from: result.count - 1, through: 1, by: -1) {
            let other = Int(generator.next() % UInt64(index + 1))
            if other != index { result.swapAt(index, other) }
        }
        return result
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

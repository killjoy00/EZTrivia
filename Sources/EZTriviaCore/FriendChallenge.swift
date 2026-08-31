import Foundation

/// A ten-question round that can be reproduced from a short, shareable code.
///
/// Friend challenges intentionally use the same shape as the Daily Challenge:
/// ten distinct categories and a 3 Easy / 4 Medium / 3 Hard progression. The
/// difference is the seed. A daily derives it from the date; a friend challenge
/// creates a fresh random seed that the result code carries to the other player.
public struct FriendChallenge: Sendable, Equatable {
    public static let questionCount = 10
    public static let codeVersion = 2
    /// Frozen for code version 2. Adding or reordering enum cases must not
    /// silently change a challenge someone has already shared; the next
    /// catalog change gets a new explicit roster and a version bump.
    public static let categoryRoster: [TriviaCategory] = [
        .football, .basketball, .soccer, .flags, .history, .science, .movies,
        .tv, .geography, .music, .animals, .food, .literature, .art
    ]
    public static let difficultyRamp: [TriviaDifficulty] = [
        .easy, .easy, .easy, .medium, .medium, .medium, .medium, .hard, .hard, .hard
    ]
    public static let maximumPoints = difficultyRamp.reduce(0) { $0 + Scoring.points(for: $1) }

    public let seed: UInt64
    public let questions: [TriviaQuestion]

    public var totalPoints: Int { questions.reduce(0) { $0 + Scoring.points(for: $1) } }

    /// Builds the identical round on every device that has this challenge-code
    /// version. Selection and answer ordering use repository-owned algorithms,
    /// rather than standard-library shuffle helpers whose exact permutations
    /// are not an API guarantee across Swift releases.
    public static func challenge(
        for seed: UInt64,
        using bank: [TriviaQuestion] = QuestionBank.all
    ) -> FriendChallenge {
        var generator = SeededGenerator(seed: seed ^ 0x4652_4945_4E44_2121)
        let categories = Array(
            categoryRoster
                .deterministicallyShuffled(using: &generator)
                .prefix(questionCount)
        )

        var questions: [TriviaQuestion] = []
        questions.reserveCapacity(questionCount)
        var usedIDs: Set<String> = []

        for slot in 0..<min(questionCount, categories.count) {
            let category = categories[slot]
            let difficulty = difficultyRamp[slot]
            let pool = bank.filter {
                $0.category == category &&
                $0.difficulty == difficulty &&
                !usedIDs.contains($0.id)
            }
            guard !pool.isEmpty else { continue }

            let index = Int(generator.next() % UInt64(pool.count))
            let picked = pool[index]
            usedIDs.insert(picked.id)
            questions.append(QuestionBank.presenting(picked, using: &generator))
        }

        return FriendChallenge(seed: seed, questions: questions)
    }
}

/// The compact hand-off between the challenger and the recipient.
///
/// Format: `EZ2-XXXX-XXXX-XXXX-XXXX-XXX`
///
/// The body is Crockford Base32 so it avoids the most easily confused letters
/// and remains practical to dictate or type. It contains a 64-bit seed, the
/// challenger's score and weighted points, plus a checksum that catches nearly
/// all paste and transcription errors. The version in `EZ2` is deliberately
/// visible: if question selection changes in a future release, that release can
/// reject or preserve old codes instead of silently serving different questions.
public struct FriendChallengeCode: Hashable, Codable, Sendable, Identifiable {
    private static let alphabet = Array("0123456789ABCDEFGHJKMNPQRSTVWXYZ")
    private static let alphabetLookup: [Character: UInt64] = Dictionary(
        uniqueKeysWithValues: alphabet.enumerated().map { ($0.element, UInt64($0.offset)) }
    )

    /// The literal that opens every code, derived from `codeVersion` rather
    /// than spelled out, so bumping the version actually changes what is
    /// printed and what is accepted. Spelling it out in two places meant a
    /// future version would still emit `EZ1` and reject genuine old codes as
    /// checksum failures -- reported to the player as a typo, which is the one
    /// thing the visible version exists to prevent.
    ///
    /// One character, so versions run 1 through 9. A tenth would need a wider
    /// field, and by then the format has earned a rethink anyway.
    static var prefix: String { prefix(for: FriendChallenge.codeVersion) }
    private static func prefix(for version: Int) -> String { "EZ\(version)" }
    private static let prefixLength = 3
    private static let bodyLength = 19

    public let version: Int
    public let seed: UInt64
    public let targetScore: Int
    public let targetPoints: Int

    public var id: String { attemptID }
    public var attemptID: String { "v\(version)-\(seed)" }

    /// Largest values the fixed-width fields can carry: one Base32 character
    /// for the score, three for the points. `codeFieldsAreWideEnough` checks
    /// these still exceed the gameplay maximums.
    static let maximumEncodableScore = 31
    static let maximumEncodablePoints = 32_767

    public init(seed: UInt64, targetScore: Int, targetPoints: Int) {
        // Debug-only, so a difficulty-ramp change that outgrows the fields is
        // caught by the test suite. A `precondition` here would instead crash a
        // player mid-round in release over a result that is merely surprising,
        // which is a far worse failure than a clamped share code.
        assert((0...FriendChallenge.questionCount).contains(targetScore))
        assert((0...FriendChallenge.maximumPoints).contains(targetPoints))

        // Clamped to what `init?(_:)` will accept, not to what the fields can
        // hold, so every code this emits parses back to the same values.
        self.version = FriendChallenge.codeVersion
        self.seed = seed
        self.targetScore = min(max(targetScore, 0), FriendChallenge.questionCount)
        self.targetPoints = min(max(targetPoints, 0), FriendChallenge.maximumPoints)
    }

    /// Parses a code while tolerating spaces, hyphens, lowercase, and the
    /// common O/0 and I/L/1 transcription mistakes.
    public init?(_ rawValue: String) {
        let compact = rawValue
            .uppercased()
            .filter { $0.isLetter || $0.isNumber }
            .map { character -> Character in
                switch character {
                case "O": "0"
                case "I", "L": "1"
                default: character
                }
            }

        guard compact.count == Self.prefixLength + Self.bodyLength,
              String(compact.prefix(Self.prefixLength)) == Self.prefix else { return nil }

        let body = Array(compact.dropFirst(Self.prefixLength))
        guard let decodedSeed = Self.decode(Array(body[0..<13])),
              let decodedScore = Self.decode(Array(body[13..<14])),
              let decodedPoints = Self.decode(Array(body[14..<17])),
              let decodedChecksum = Self.decode(Array(body[17..<19])),
              decodedScore <= UInt64(FriendChallenge.questionCount),
              decodedPoints <= UInt64(FriendChallenge.maximumPoints) else { return nil }

        let score = Int(decodedScore)
        let points = Int(decodedPoints)
        guard decodedChecksum == Self.checksum(
            seed: decodedSeed,
            score: score,
            points: points,
            version: FriendChallenge.codeVersion
        ) else {
            return nil
        }

        version = FriendChallenge.codeVersion
        seed = decodedSeed
        targetScore = score
        targetPoints = points
    }

    public var displayString: String {
        let seedPart = Self.encode(seed, width: 13)
        let scorePart = Self.encode(UInt64(targetScore), width: 1)
        let pointsPart = Self.encode(UInt64(targetPoints), width: 3)
        let checksumPart = Self.encode(
            Self.checksum(seed: seed, score: targetScore, points: targetPoints, version: version),
            width: 2
        )
        let body = seedPart + scorePart + pointsPart + checksumPart
        let groups = stride(from: 0, to: body.count, by: 4).map { offset in
            let start = body.index(body.startIndex, offsetBy: offset)
            let end = body.index(start, offsetBy: min(4, body.count - offset))
            return String(body[start..<end])
        }
        return Self.prefix(for: version) + "-" + groups.joined(separator: "-")
    }

    private static func encode(_ value: UInt64, width: Int) -> String {
        var remaining = value
        var characters = Array(repeating: Character("0"), count: width)
        for index in characters.indices.reversed() {
            characters[index] = alphabet[Int(remaining & 31)]
            remaining >>= 5
        }
        return String(characters)
    }

    private static func decode(_ characters: [Character]) -> UInt64? {
        var value: UInt64 = 0
        for character in characters {
            guard let digit = alphabetLookup[character],
                  value <= (UInt64.max - digit) / 32 else { return nil }
            value = value * 32 + digit
        }
        return value
    }

    /// A compact non-cryptographic integrity check. The code is not a security
    /// boundary; the checksum exists to turn a mistyped code into a clear error
    /// rather than a valid-looking but different challenge.
    private static func checksum(seed: UInt64, score: Int, points: Int, version: Int) -> UInt64 {
        var value = seed
        value ^= UInt64(score) &* 0x9E37_79B9_7F4A_7C15
        value ^= UInt64(points) &* 0xBF58_476D_1CE4_E5B9
        value ^= UInt64(version) &* 0x94D0_49BB_1331_11EB
        value ^= value >> 30
        value &*= 0xBF58_476D_1CE4_E5B9
        value ^= value >> 27
        value &*= 0x94D0_49BB_1331_11EB
        value ^= value >> 31
        return value & 0x3FF
    }
}

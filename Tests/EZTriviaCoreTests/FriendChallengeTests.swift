import Foundation
import Testing
@testable import EZTriviaCore

@Test func friendChallengeIsIdenticalForTheSameSeed() {
    let first = FriendChallenge.challenge(for: 0x1234_5678_9ABC_DEF0)
    let second = FriendChallenge.challenge(for: 0x1234_5678_9ABC_DEF0)

    #expect(first.questions.map(\.id) == second.questions.map(\.id))
    #expect(first.questions.map(\.answers) == second.questions.map(\.answers))
    #expect(first.questions.map(\.correctAnswerIndex) == second.questions.map(\.correctAnswerIndex))
}

@Test func friendChallengeUsesTheDailyStyleMix() {
    for seed in stride(from: UInt64(1), through: 500, by: 37) {
        let challenge = FriendChallenge.challenge(for: seed)
        #expect(challenge.questions.count == FriendChallenge.questionCount)
        #expect(Set(challenge.questions.map(\.id)).count == FriendChallenge.questionCount)
        #expect(Set(challenge.questions.map(\.category)).count == FriendChallenge.questionCount)
        #expect(challenge.questions.map(\.difficulty) == FriendChallenge.difficultyRamp)
        #expect(challenge.totalPoints == FriendChallenge.maximumPoints)
    }
}

@Test func friendChallengeWithAFlagIsAlsoReproducible() {
    guard let seed = (0..<100).map { UInt64($0) }.first(where: { seed in
        FriendChallenge.challenge(for: seed).questions.contains { $0.category == .flags }
    }) else {
        Issue.record("no friend challenge in the first 100 seeds contains a flag")
        return
    }

    let first = FriendChallenge.challenge(for: seed)
    let second = FriendChallenge.challenge(for: seed)
    #expect(first.questions.map(\.answers) == second.questions.map(\.answers))
    #expect(first.questions.map(\.correctAnswerIndex) == second.questions.map(\.correctAnswerIndex))
}

@Test func challengeCodeRoundTripsItsSeedAndTarget() {
    let original = FriendChallengeCode(
        seed: 0xFEDC_BA98_7654_3210,
        targetScore: 8,
        targetPoints: 1_350
    )
    let decoded = FriendChallengeCode(original.displayString)

    #expect(original.displayString == "EZ3-FXQ5-TK1V-58CG-G81A-6G4")
    #expect(decoded == original)
    #expect(decoded?.attemptID == original.attemptID)
}

@Test func friendChallengeVersionOwnsAnExplicitCategoryRoster() {
    #expect(FriendChallenge.categoryRoster == TriviaCategory.allCases)
    #expect(Set(FriendChallenge.categoryRoster).count == TriviaCategory.allCases.count)
}

@Test func theVisibleVersionComesFromTheVersionConstant() {
    // Encoding and decoding both have to read the version from one place. When
    // the prefix was spelled out as a literal in each, bumping the constant
    // would have kept printing the old prefix and rejected genuine older codes
    // as checksum failures -- which the player is shown as "that's a typo".
    #expect(FriendChallengeCode.prefix == "EZ\(FriendChallenge.codeVersion)")
    let code = FriendChallengeCode(seed: 7, targetScore: 3, targetPoints: 400)
    #expect(code.displayString.hasPrefix(FriendChallengeCode.prefix + "-"))
    #expect(FriendChallengeCode(code.displayString) == code)
}

@Test func codeFieldsAreWideEnough() {
    // The score and points fields are fixed-width, so a retuned difficulty
    // ramp could outgrow them. This is the check that catches that at test
    // time rather than by emitting codes that will not parse back.
    #expect(FriendChallenge.questionCount <= FriendChallengeCode.maximumEncodableScore)
    #expect(FriendChallenge.maximumPoints <= FriendChallengeCode.maximumEncodablePoints)
}

@Test func aPerfectChallengeRoundTrips() {
    // The upper bound of both fields as gameplay can actually produce it.
    let best = FriendChallengeCode(
        seed: .max,
        targetScore: FriendChallenge.questionCount,
        targetPoints: FriendChallenge.maximumPoints
    )
    let decoded = FriendChallengeCode(best.displayString)
    #expect(decoded == best)
    #expect(decoded?.targetScore == FriendChallenge.questionCount)
    #expect(decoded?.targetPoints == FriendChallenge.maximumPoints)
}

@Test func challengeCodeAcceptsFriendlyFormattingButRejectsTypos() {
    let original = FriendChallengeCode(seed: 42, targetScore: 7, targetPoints: 1_100)
    let looselyFormatted = original.displayString
        .lowercased()
        .replacingOccurrences(of: "-", with: " ")
    #expect(FriendChallengeCode(looselyFormatted) == original)

    var damaged = Array(original.displayString)
    let finalIndex = damaged.index(before: damaged.endIndex)
    damaged[finalIndex] = damaged[finalIndex] == "0" ? "1" : "0"
    #expect(FriendChallengeCode(String(damaged)) == nil)
}

@Test func anOlderCodeIsNamedAsOldRatherThanMistyped() {
    // A v2 code is copied perfectly; it is the roster behind it this build can
    // no longer reproduce. Reporting a typo would send its holder hunting for
    // a mistake that is not there.
    let previousVersion = "EZ2-FXQ5-TK1V-58CG-G81A-6WA"
    #expect(FriendChallengeCode(previousVersion) == nil)
    #expect(FriendChallengeCode.rejectionReason(for: previousVersion) == .unsupportedVersion(2))
    #expect(FriendChallengeCode.rejectionReason(for: "EZ1-FXQ5-TK1V-58CG-G81A-6JQ") == .unsupportedVersion(1))
}

@Test func genuineTyposAreStillReportedAsTypos() {
    let valid = FriendChallengeCode(seed: 42, targetScore: 7, targetPoints: 1_100).displayString
    #expect(FriendChallengeCode.rejectionReason(for: valid) == nil)

    // Right shape and right version, wrong checksum.
    var damaged = Array(valid)
    let last = damaged.index(before: damaged.endIndex)
    damaged[last] = damaged[last] == "0" ? "1" : "0"
    #expect(FriendChallengeCode.rejectionReason(for: String(damaged)) == .unreadable)

    #expect(FriendChallengeCode.rejectionReason(for: "not a code at all") == .unreadable)
    #expect(FriendChallengeCode.rejectionReason(for: "") == .unreadable)
}

@Test func storedVersionOneResultsKeepTheirOriginalDisplayCode() throws {
    let json = #"{"version":1,"seed":18364758544493064720,"targetScore":8,"targetPoints":1350}"#
    let legacy = try JSONDecoder().decode(FriendChallengeCode.self, from: Data(json.utf8))

    #expect(legacy.displayString == "EZ1-FXQ5-TK1V-58CG-G81A-6JQ")
    #expect(FriendChallengeCode(legacy.displayString) == nil)
    #expect(legacy.attemptID == "v1-18364758544493064720")
}

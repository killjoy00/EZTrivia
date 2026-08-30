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

    #expect(original.displayString == "EZ1-FXQ5-TK1V-58CG-G81A-6JQ")
    #expect(decoded == original)
    #expect(decoded?.attemptID == original.attemptID)
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

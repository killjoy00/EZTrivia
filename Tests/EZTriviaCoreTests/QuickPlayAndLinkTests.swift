import Foundation
import Testing
@testable import EZTriviaCore

@Test func quickPlayUsesTenDistinctCategoriesAndTheSharedDifficultyRamp() {
    for _ in 0..<50 {
        let round = QuestionPicker.quickPlayRound()
        #expect(round.count == 10)
        #expect(Set(round.map(\.id)).count == 10)
        #expect(Set(round.map(\.category)).count == 10)
        #expect(round.map(\.difficulty) == QuestionPicker.quickPlayDifficultyRamp)
    }
}

@Test func quickPlayPrefersUnseenQuestionsWithinEachChosenSlot() {
    let excluded = Set(QuestionBank.all.prefix(250).map(\.id))
    let round = QuestionPicker.quickPlayRound(excluding: excluded)

    #expect(round.count == 10)
    #expect(Set(round.map(\.id)).count == 10)
    // An excluded question is allowed only when its category/difficulty slot
    // has no unseen alternative. Every shipped pool is much deeper than one,
    // so the current catalog should need no such fallback.
    #expect(round.allSatisfy { !excluded.contains($0.id) })
}

@Test func quickPlayShareTextIncludesScoreGridPointsAndStoreLink() {
    let outcomes = [true, false, true, true, false, true, false, true, true, true]
    let text = RoundSummary.quickPlay(outcomes: outcomes, points: 1_350)

    #expect(text.contains("Quick Play — 7/10"))
    #expect(text.contains(RoundSummary.grid(outcomes)))
    #expect(text.contains("1,350 points"))
    #expect(text.contains(RoundSummary.appStoreURL))
}

@Test func friendChallengeDeepLinkRoundTripsTheCode() {
    let code = FriendChallengeCode(seed: 0x1234_5678_9ABC_DEF0, targetScore: 8, targetPoints: 1_350)
    let url = FriendChallengeLink.url(for: code)

    #expect(url.scheme == "eztrivia")
    #expect(url.host == "challenge")
    #expect(FriendChallengeLink.code(from: url) == code)
}

@Test func unrelatedURLsDoNotBecomeFriendChallenges() {
    #expect(FriendChallengeLink.code(from: URL(string: "https://example.com/challenge/EZ3-AAAA")!) == nil)
    #expect(FriendChallengeLink.code(from: URL(string: "eztrivia://settings")!) == nil)
    #expect(FriendChallengeLink.code(from: URL(string: "eztrivia://challenge/not-a-code")!) == nil)
}

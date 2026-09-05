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
    var excluded: Set<String> = []
    for category in TriviaCategory.allCases {
        for difficulty in TriviaDifficulty.allCases {
            if let first = QuestionBank.all.first(where: {
                $0.category == category && $0.difficulty == difficulty
            }) {
                excluded.insert(first.id)
            }
        }
    }

    for _ in 0..<25 {
        let round = QuestionPicker.quickPlayRound(excluding: excluded)
        #expect(round.count == 10)
        #expect(Set(round.map(\.id)).count == 10)
        #expect(round.allSatisfy { !excluded.contains($0.id) })
    }
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

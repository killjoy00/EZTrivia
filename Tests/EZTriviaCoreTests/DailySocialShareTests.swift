import Testing
@testable import EZTriviaCore

@Test func dailyShareCanIncludeLiveStanding() {
    let text = RoundSummary.daily(
        day: 42,
        outcomes: [true, false, true, true],
        points: 450,
        streak: 6,
        globalRank: 18,
        topPercent: 8
    )

    #expect(text.contains("#18 today"))
    #expect(text.contains("Top 8%"))
    #expect(text.contains("6 day streak"))
    #expect(text.split(separator: "\n").last == Substring(RoundSummary.appStoreURL))
}

@Test func dailyShareStillWorksBeforeAStandingLoads() {
    let text = RoundSummary.daily(
        day: 42,
        outcomes: [true, false],
        points: 100,
        streak: 1
    )

    #expect(!text.contains("today · Top"))
    #expect(text.contains(RoundSummary.grid([true, false])))
    #expect(text.split(separator: "\n").last == Substring(RoundSummary.appStoreURL))
}

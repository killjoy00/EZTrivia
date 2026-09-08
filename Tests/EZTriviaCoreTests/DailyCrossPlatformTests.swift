import Foundation
import Testing
@testable import EZTriviaCore

private func dailyV2Fingerprint(_ challenge: DailyChallenge) -> UInt64 {
    let payload = challenge.questions.map { question in
        "\(question.id)\u{1F}\(question.correctAnswerIndex)\u{1F}\(question.answers.joined(separator: "\u{1E}"))"
    }.joined(separator: "\u{1D}")

    var hash: UInt64 = 14_695_981_039_346_656_037
    for byte in payload.utf8 {
        hash ^= UInt64(byte)
        hash = hash &* 1_099_511_628_211
    }
    return hash
}

@Test func crossPlatformDailyStartsAtSeptemberNinthWithoutRewritingSeptemberEighth() throws {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = try #require(TimeZone(identifier: "America/Chicago"))

    let septemberEight = try #require(calendar.date(from: DateComponents(year: 2026, month: 9, day: 8, hour: 12)))
    let septemberNine = try #require(calendar.date(from: DateComponents(year: 2026, month: 9, day: 9, hour: 12)))

    #expect(DailyChallenge.day(for: septemberEight, in: calendar) == 250)
    #expect(DailyChallenge.day(for: septemberNine, in: calendar) == DailyChallenge.crossPlatformStartDay)
    #expect(DailyChallenge.crossPlatformStartDay == 251)
    #expect(DailyChallenge.crossPlatformAlgorithmVersion == 2)
}

@Test func dailyV2UsesOnlyRepositoryOwnedDeterminism() {
    for day in [251, 252, 365, 512, 1_024] {
        let first = DailyChallenge.crossPlatformChallenge(for: day)
        let second = DailyChallenge.crossPlatformChallenge(for: day)

        #expect(first.questions.count == DailyChallenge.questionCount)
        #expect(first.questions.map(\.id) == second.questions.map(\.id))
        #expect(first.questions.map(\.answers) == second.questions.map(\.answers))
        #expect(first.questions.map(\.correctAnswerIndex) == second.questions.map(\.correctAnswerIndex))
        #expect(first.questions.map(\.difficulty) == DailyChallenge.ramp)
        #expect(Set(first.questions.map(\.category)).count == DailyChallenge.questionCount)
    }
}

@Test func dailyV2FingerprintIsAvailableForTheKotlinGoldenTest() {
    let days = [251, 252, 365, 512]
    let fingerprints = days.map { day in
        (day, dailyV2Fingerprint(DailyChallenge.crossPlatformChallenge(for: day)))
    }

    // Keep this compact line in CI logs. The Android test pins the same values;
    // a catalog or algorithm edit must update both clients deliberately.
    print("DAILY_V2_GOLDEN " + fingerprints.map { "\($0.0)=\($0.1)" }.joined(separator: " "))
    #expect(Set(fingerprints.map(\.1)).count == fingerprints.count)
}

@Test func publicDailyBuilderSwitchesToV2AtTheBoundary() {
    let atBoundary = DailyChallenge.challenge(for: DailyChallenge.crossPlatformStartDay)
    let explicitV2 = DailyChallenge.crossPlatformChallenge(for: DailyChallenge.crossPlatformStartDay)

    #expect(atBoundary.questions == explicitV2.questions)
    #expect(DailyChallenge.challenge(for: 250).questions.count == DailyChallenge.questionCount)
}

import Foundation
import Testing
@testable import EZTriviaCore

private func context(
    score: Int = 9,
    total: Int = 10,
    roundsCompleted: Int = 20,
    currentVersion: String = "1.1.0",
    lastPromptedVersion: String? = nil,
    daysSinceLastPrompt: Int? = nil
) -> ReviewPromptPolicy.Context {
    .init(
        score: score,
        total: total,
        roundsCompleted: roundsCompleted,
        currentVersion: currentVersion,
        lastPromptedVersion: lastPromptedVersion,
        daysSinceLastPrompt: daysSinceLastPrompt
    )
}

@Test func aStrongRoundFromASettledPlayerEarnsTheAsk() {
    #expect(ReviewPromptPolicy.shouldAsk(context()))
}

@Test func aPoorRoundNeverEarnsTheAsk() {
    // The whole point of gating on the score: a prompt here collects the
    // rating the round earned, not the one the app earned.
    #expect(!ReviewPromptPolicy.shouldAsk(context(score: 3, total: 10)))
    #expect(!ReviewPromptPolicy.shouldAsk(context(score: 6, total: 10)))
    // Exactly at the threshold still counts.
    #expect(ReviewPromptPolicy.shouldAsk(context(score: 7, total: 10)))
}

@Test func aNewPlayerIsNotAskedForAFavor() {
    #expect(!ReviewPromptPolicy.shouldAsk(context(roundsCompleted: 1)))
    #expect(!ReviewPromptPolicy.shouldAsk(context(roundsCompleted: ReviewPromptPolicy.minimumRounds - 1)))
    #expect(ReviewPromptPolicy.shouldAsk(context(roundsCompleted: ReviewPromptPolicy.minimumRounds)))
}

@Test func oneAskPerVersion() {
    #expect(!ReviewPromptPolicy.shouldAsk(
        context(currentVersion: "1.1.0", lastPromptedVersion: "1.1.0", daysSinceLastPrompt: 900)
    ))
    // A later release may ask again, once the spacing has also elapsed.
    #expect(ReviewPromptPolicy.shouldAsk(
        context(currentVersion: "1.2.0", lastPromptedVersion: "1.1.0", daysSinceLastPrompt: 900)
    ))
}

@Test func promptsAreSpacedOutEvenAcrossVersions() {
    // Shipping four versions in a month must not spend four of the three
    // prompts iOS allows in a year.
    #expect(!ReviewPromptPolicy.shouldAsk(
        context(currentVersion: "1.4.0", lastPromptedVersion: "1.3.0", daysSinceLastPrompt: 10)
    ))
    #expect(ReviewPromptPolicy.shouldAsk(
        context(
            currentVersion: "1.4.0",
            lastPromptedVersion: "1.3.0",
            daysSinceLastPrompt: ReviewPromptPolicy.minimumDaysBetweenPrompts
        )
    ))
}

@Test func anEmptyRoundIsNeverAPromptMoment() {
    // Guards the division as much as the policy.
    #expect(!ReviewPromptPolicy.shouldAsk(context(score: 0, total: 0)))
}

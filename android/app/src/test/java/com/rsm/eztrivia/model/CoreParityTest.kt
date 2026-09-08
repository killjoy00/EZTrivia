package com.rsm.eztrivia.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreParityTest {
    @Test
    fun scoringMatchesIos() {
        assertEquals(100, Scoring.points(TriviaDifficulty.EASY))
        assertEquals(150, Scoring.points(TriviaDifficulty.MEDIUM))
        assertEquals(250, Scoring.points(TriviaDifficulty.HARD))
        assertEquals(1_650, QuestionPicker.quickPlayDifficultyRamp.sumOf(Scoring::points))
        assertEquals(1_650, FriendChallenge.MAXIMUM_POINTS)
    }

    @Test
    fun categoryRosterMatchesIos() {
        assertEquals(16, TriviaCategory.entries.size)
        assertEquals("football", TriviaCategory.entries.first().wireName)
        assertEquals("videoGames", TriviaCategory.entries.last().wireName)
        assertEquals(TriviaCategory.entries, FriendChallenge.categoryRoster)
    }

    @Test
    fun engineScoresAndAdvancesLikeIos() {
        val question = TriviaQuestion(
            id = "test-1",
            category = TriviaCategory.SCIENCE,
            prompt = "Test?",
            difficulty = TriviaDifficulty.HARD,
            visual = null,
            answers = listOf("A", "B", "C", "D"),
            correctAnswerIndex = 2,
            explanation = "C is correct.",
        )
        val engine = TriviaEngine(listOf(question))

        assertTrue(engine.answer(2))
        assertEquals(1, engine.score)
        assertEquals(250, engine.points)
        assertEquals(listOf(true), engine.outcomes)
        assertTrue(engine.advance())
        assertTrue(engine.isRoundComplete)
        assertNull(engine.currentQuestion)
        assertFalse(engine.advance())
    }

    @Test
    fun friendChallengeCodeMatchesIosGoldenVector() {
        val code = FriendChallengeCode(
            seed = 0xFEDCBA9876543210UL,
            targetScore = 8,
            targetPoints = 1_350,
        )
        assertEquals("EZ3-FXQ5-TK1V-58CG-G81A-6G4", code.displayString)
        assertEquals(code, FriendChallengeCode.parse(code.displayString))
    }

    @Test
    fun perfectFriendChallengeFitsTheWireFormat() {
        val code = FriendChallengeCode(
            seed = ULong.MAX_VALUE,
            targetScore = 10,
            targetPoints = 1_650,
        )
        assertEquals(code, FriendChallengeCode.parse(code.displayString))
    }

    @Test
    fun olderFriendChallengeVersionIsNamedAsUnsupported() {
        val reason = FriendChallengeCode.rejectionReason("EZ2-FXQ5-TK1V-58CG-G81A-6WA")
        assertEquals(FriendChallengeCode.RejectionReason.UnsupportedVersion(2), reason)
    }

    @Test
    fun friendChallengeAttemptIdentityIgnoresClaimedTarget() {
        val first = FriendChallengeCode(seed = 42UL, targetScore = 2, targetPoints = 300)
        val second = FriendChallengeCode(seed = 42UL, targetScore = 10, targetPoints = 1_650)
        assertEquals(first.attemptId, second.attemptId)
    }

    @Test
    fun friendChallengeLinksRoundTripAcrossIosAndAndroidForms() {
        val code = FriendChallengeCode(seed = 42UL, targetScore = 7, targetPoints = 1_100)
        assertEquals(code, FriendChallengeLink.codeFrom(code.displayString))
        assertEquals(code, FriendChallengeLink.codeFrom(FriendChallengeLink.customUrl(code)))
        assertEquals(code, FriendChallengeLink.codeFrom(FriendChallengeLink.webUrl(code)))
        assertNull(FriendChallengeLink.codeFrom("https://example.com/challenge/${code.displayString}"))
    }

    @Test
    fun deterministicFriendChallengeUsesTheSameMixEveryTime() {
        val bank = syntheticBank()
        val first = FriendChallenge.challenge(0x123456789ABCDEF0UL, bank)
        val second = FriendChallenge.challenge(0x123456789ABCDEF0UL, bank)

        assertEquals(FriendChallenge.QUESTION_COUNT, first.size)
        assertEquals(first.map(TriviaQuestion::id), second.map(TriviaQuestion::id))
        assertEquals(first.map(TriviaQuestion::answers), second.map(TriviaQuestion::answers))
        assertEquals(first.map(TriviaQuestion::correctAnswerIndex), second.map(TriviaQuestion::correctAnswerIndex))
        assertEquals(FriendChallenge.QUESTION_COUNT, first.map(TriviaQuestion::category).toSet().size)
        assertEquals(FriendChallenge.difficultyRamp, first.map(TriviaQuestion::difficulty))
    }

    @Test
    fun deterministicFlagPresentationRedrawsFourStableOptions() {
        val bank = syntheticBank()
        val seed = (0UL..200UL).first { candidate ->
            FriendChallenge.challenge(candidate, bank).any { it.category == TriviaCategory.FLAGS }
        }
        val firstFlag = FriendChallenge.challenge(seed, bank).first { it.category == TriviaCategory.FLAGS }
        val secondFlag = FriendChallenge.challenge(seed, bank).first { it.category == TriviaCategory.FLAGS }

        assertEquals(4, firstFlag.answers.size)
        assertEquals(4, firstFlag.answers.toSet().size)
        assertEquals(firstFlag.answers, secondFlag.answers)
        assertEquals(firstFlag.correctAnswerIndex, secondFlag.correctAnswerIndex)
        assertEquals(firstFlag.flagCode, firstFlag.answers[firstFlag.correctAnswerIndex].removePrefix("Flag "))
    }

    private fun syntheticBank(): List<TriviaQuestion> = buildList {
        TriviaCategory.entries.forEach { category ->
            TriviaDifficulty.entries.forEach { difficulty ->
                repeat(if (category == TriviaCategory.FLAGS) 6 else 4) { index ->
                    val suffix = "${category.wireName}-${difficulty.wireName}-$index"
                    if (category == TriviaCategory.FLAGS) {
                        val code = "${difficulty.ordinal}${index}"
                        add(
                            TriviaQuestion(
                                id = "flag-$suffix",
                                category = category,
                                prompt = "Which flag?",
                                difficulty = difficulty,
                                visual = "flag-${code.lowercase()}",
                                answers = listOf("Flag $code", "A $suffix", "B $suffix", "C $suffix"),
                                correctAnswerIndex = 0,
                                explanation = "Flag $code is correct.",
                                flagCode = code,
                            )
                        )
                    } else {
                        add(
                            TriviaQuestion(
                                id = suffix,
                                category = category,
                                prompt = "Question $suffix?",
                                difficulty = difficulty,
                                visual = null,
                                answers = listOf("Correct $suffix", "A $suffix", "B $suffix", "C $suffix"),
                                correctAnswerIndex = 0,
                                explanation = "Correct $suffix is correct.",
                            )
                        )
                    }
                }
            }
        }
    }
}

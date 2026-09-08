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
    }

    @Test
    fun categoryRosterMatchesIos() {
        assertEquals(16, TriviaCategory.entries.size)
        assertEquals("football", TriviaCategory.entries.first().wireName)
        assertEquals("videoGames", TriviaCategory.entries.last().wireName)
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
    fun olderFriendChallengeVersionIsNamedAsUnsupported() {
        val reason = FriendChallengeCode.rejectionReason("EZ2-FXQ5-TK1V-58CG-G81A-6WA")
        assertEquals(FriendChallengeCode.RejectionReason.UnsupportedVersion(2), reason)
    }
}

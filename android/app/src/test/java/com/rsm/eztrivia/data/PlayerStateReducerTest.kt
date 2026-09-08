package com.rsm.eztrivia.data

import com.rsm.eztrivia.model.TriviaCategory
import com.rsm.eztrivia.model.TriviaDifficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerStateReducerTest {
    @Test
    fun seenQuestionCycleMatchesIosSemantics() {
        val first = PlayerStateReducer.markSeen(
            state = PlayerState(),
            ids = setOf("q1", "q2"),
            category = TriviaCategory.SCIENCE,
            difficulty = TriviaDifficulty.EASY,
            availableCount = 4,
        )
        assertEquals(
            setOf("q1", "q2"),
            first.seenQuestions(TriviaCategory.SCIENCE, TriviaDifficulty.EASY),
        )

        val exhausted = PlayerStateReducer.markSeen(
            state = first,
            ids = setOf("q3", "q4"),
            category = TriviaCategory.SCIENCE,
            difficulty = TriviaDifficulty.EASY,
            availableCount = 4,
        )
        assertEquals(
            setOf("q3", "q4"),
            exhausted.seenQuestions(TriviaCategory.SCIENCE, TriviaDifficulty.EASY),
        )
    }

    @Test
    fun questionProgressIsMonotonic() {
        val wrong = PlayerStateReducer.recordQuestionAnswer(PlayerState(), "q1", correct = false)
        assertTrue("q1" in wrong.completedQuestionIds)
        assertFalse("q1" in wrong.correctlyAnsweredQuestionIds)

        val laterCorrect = PlayerStateReducer.recordQuestionAnswer(wrong, "q1", correct = true)
        assertTrue("q1" in laterCorrect.completedQuestionIds)
        assertTrue("q1" in laterCorrect.correctlyAnsweredQuestionIds)

        val wrongAgain = PlayerStateReducer.recordQuestionAnswer(laterCorrect, "q1", correct = false)
        assertEquals(laterCorrect, wrongAgain)
    }

    @Test
    fun categoryRoundAddsLifetimePointsAndAchievementFacts() {
        val next = PlayerStateReducer.recordCategoryRound(
            state = PlayerState(),
            category = TriviaCategory.HISTORY,
            difficulty = TriviaDifficulty.HARD,
            score = 10,
            total = 10,
            points = 2_500,
            id = "round-1",
            dateMillis = 100,
        )

        assertEquals(1, next.totalRoundsCompleted)
        assertEquals(2_500, next.lifetimePointsByCategory["history"])
        assertTrue("history" in next.playedCategoryRawValues)
        assertTrue("hard" in next.perfectDifficultyRawValues)
        assertEquals("round-1", next.recentCategoryResults.single().id)
    }

    @Test
    fun quickPlayHistoryCapsAtTwentyButCountersKeepGrowing() {
        var state = PlayerState()
        repeat(25) { index ->
            state = PlayerStateReducer.recordQuickPlay(
                state = state,
                score = index % 11,
                total = 10,
                points = 1_000 + index,
                outcomes = List(10) { it < (index % 11).coerceAtMost(10) },
                categories = setOf(TriviaCategory.FOOTBALL, TriviaCategory.MUSIC),
                id = "quick-$index",
                dateMillis = index.toLong(),
            )
        }

        assertEquals(25, state.totalRoundsCompleted)
        assertEquals(25, state.quickPlayRoundsCompleted)
        assertEquals(20, state.quickPlayResults.size)
        assertEquals("quick-24", state.quickPlayResults.first().id)
        assertTrue("football" in state.playedCategoryRawValues)
        assertTrue("music" in state.playedCategoryRawValues)
    }
}

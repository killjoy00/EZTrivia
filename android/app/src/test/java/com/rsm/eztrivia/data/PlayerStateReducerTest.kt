package com.rsm.eztrivia.data

import com.rsm.eztrivia.model.FriendChallengeCode
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

    @Test
    fun friendChallengeIsOneAttemptAndDoesNotAddLifetimeCategoryPoints() {
        val originalCode = FriendChallengeCode(seed = 42UL, targetScore = 7, targetPoints = 1_100)
        val firstResult = FriendChallengeResult(
            code = originalCode,
            score = 8,
            total = 10,
            points = 1_250,
            outcomes = List(10) { it < 8 },
            createdChallenge = false,
            dateMillis = 100,
        )
        val first = PlayerStateReducer.recordFriendChallenge(
            state = PlayerState(),
            result = firstResult,
            categories = setOf(TriviaCategory.SCIENCE, TriviaCategory.MUSIC),
        )

        assertEquals(1, first.friendChallengesCompleted)
        assertEquals(1, first.totalRoundsCompleted)
        assertTrue(first.lifetimePointsByCategory.isEmpty())
        assertTrue("science" in first.playedCategoryRawValues)
        assertTrue("music" in first.playedCategoryRawValues)

        val alteredTargetSameSeed = FriendChallengeResult(
            code = FriendChallengeCode(seed = 42UL, targetScore = 1, targetPoints = 100),
            score = 10,
            total = 10,
            points = 1_650,
            outcomes = List(10) { true },
            createdChallenge = false,
            dateMillis = 200,
        )
        val replay = PlayerStateReducer.recordFriendChallenge(
            state = first,
            result = alteredTargetSameSeed,
            categories = setOf(TriviaCategory.HISTORY),
        )

        assertEquals(first, replay)
        assertEquals(firstResult, replay.friendChallengeResult(originalCode))
    }

    @Test
    fun clearingRecentHistoryKeepsPermanentProgress() {
        val friendCode = FriendChallengeCode(seed = 99UL, targetScore = 6, targetPoints = 950)
        val friendResult = FriendChallengeResult(
            code = friendCode,
            score = 7,
            total = 10,
            points = 1_100,
            outcomes = List(10) { it < 7 },
            createdChallenge = false,
            dateMillis = 300,
        )
        val original = PlayerState(
            recentCategoryResults = listOf(
                CategoryRoundResult(
                    id = "round-1",
                    category = "history",
                    difficulty = "hard",
                    score = 9,
                    total = 10,
                    dateMillis = 100,
                ),
            ),
            quickPlayResults = listOf(
                QuickPlayResult(
                    id = "quick-1",
                    score = 8,
                    total = 10,
                    points = 1_200,
                    outcomes = List(10) { it < 8 },
                    dateMillis = 200,
                ),
            ),
            friendChallengeResultsByAttemptId = mapOf(friendCode.attemptId to friendResult),
            seenQuestionIds = mapOf("history-hard" to setOf("q1", "q2")),
            completedQuestionIds = setOf("q1", "q2"),
            correctlyAnsweredQuestionIds = setOf("q1"),
            lifetimePointsByCategory = mapOf("history" to 2_000),
            totalRoundsCompleted = 7,
            quickPlayRoundsCompleted = 1,
            playedCategoryRawValues = setOf("history"),
            perfectDifficultyRawValues = setOf("easy"),
        )

        val cleared = PlayerStateReducer.clearRecentCategoryHistory(original)

        assertTrue(cleared.recentCategoryResults.isEmpty())
        assertTrue(cleared.seenQuestionIds.isEmpty())
        assertEquals(original.quickPlayResults, cleared.quickPlayResults)
        assertEquals(original.friendChallengeResultsByAttemptId, cleared.friendChallengeResultsByAttemptId)
        assertEquals(original.completedQuestionIds, cleared.completedQuestionIds)
        assertEquals(original.correctlyAnsweredQuestionIds, cleared.correctlyAnsweredQuestionIds)
        assertEquals(original.lifetimePointsByCategory, cleared.lifetimePointsByCategory)
        assertEquals(original.totalRoundsCompleted, cleared.totalRoundsCompleted)
        assertEquals(original.quickPlayRoundsCompleted, cleared.quickPlayRoundsCompleted)
        assertEquals(original.playedCategoryRawValues, cleared.playedCategoryRawValues)
        assertEquals(original.perfectDifficultyRawValues, cleared.perfectDifficultyRawValues)
    }
}

package com.rsm.eztrivia.data

import com.rsm.eztrivia.model.DailyStreak
import com.rsm.eztrivia.model.TriviaCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class DailyPlayerStateReducerTest {
    @Test
    fun firstCompletedDailyWinsAndDoesNotAddLifetimeCategoryPoints() {
        val first = DailyResult(
            day = 251,
            score = 6,
            total = 10,
            points = 900,
            outcomes = listOf(true, true, false, true, false, true, false, true, true, false),
            dateMillis = 1L,
        )
        val betterReplay = first.copy(score = 10, points = 1_650, outcomes = List(10) { true }, dateMillis = 2L)

        val afterFirst = PlayerStateReducer.recordDaily(
            state = PlayerState(),
            result = first,
            categories = setOf(TriviaCategory.HISTORY, TriviaCategory.SCIENCE),
        )
        val afterReplay = PlayerStateReducer.recordDaily(
            state = afterFirst,
            result = betterReplay,
            categories = setOf(TriviaCategory.HISTORY, TriviaCategory.SCIENCE),
        )

        assertEquals(first, afterFirst.dailyResult(251))
        assertEquals(1, afterFirst.totalRoundsCompleted)
        assertEquals(0, afterFirst.lifetimePointsTotal)
        assertEquals(setOf("history", "science"), afterFirst.playedCategoryRawValues)
        assertSame(afterFirst, afterReplay)
    }

    @Test
    fun clearingRecentCategoryHistoryPreservesDailyHistory() {
        val result = DailyResult(
            day = 251,
            score = 5,
            total = 10,
            points = 700,
            outcomes = List(10) { it < 5 },
            dateMillis = 1L,
        )
        val withDaily = PlayerStateReducer.recordDaily(PlayerState(), result, emptySet())
        val cleared = PlayerStateReducer.clearRecentCategoryHistory(withDaily)

        assertEquals(result, cleared.dailyResult(251))
        assertFalse(cleared.dailyResultsByDay.isEmpty())
    }

    @Test
    fun streakSurvivesUntilAnEntireDayIsMissed() {
        assertEquals(3, DailyStreak.current(setOf(251, 252, 253), 253))
        assertEquals(3, DailyStreak.current(setOf(251, 252, 253), 254))
        assertEquals(0, DailyStreak.current(setOf(251, 252, 253), 255))
    }
}

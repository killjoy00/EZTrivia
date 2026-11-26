package com.rsm.eztrivia.playgames

import com.rsm.eztrivia.model.TriviaCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayGamesBoardTest {
    @Test
    fun dailyFirstThenEveryCategoryMatchesIosBoardSelector() {
        val boards = PlayGamesBoard.all
        assertEquals(17, boards.size)
        assertEquals(PlayGamesIds.dailyLeaderboard, boards.first().id)
        assertEquals("Daily Challenge", boards.first().title)
        assertTrue(boards.first().isDaily)
        assertEquals(
            TriviaCategory.entries.map(TriviaCategory::title),
            boards.drop(1).map(PlayGamesBoard::title),
        )
        assertEquals(
            TriviaCategory.entries.map { PlayGamesIds.categoryLeaderboards.getValue(it) },
            boards.drop(1).map(PlayGamesBoard::id),
        )
        assertTrue(boards.drop(1).all { !it.isDaily })
        assertEquals(17, boards.map(PlayGamesBoard::id).toSet().size)
        assertFalse(boards.any { it.id.isBlank() })
    }
}

package com.rsm.eztrivia.playgames

import com.rsm.eztrivia.model.AchievementCatalog
import com.rsm.eztrivia.model.TriviaCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayGamesIdsTest {
    @Test
    fun everyLocalAchievementHasExactlyOnePlayGamesResource() {
        val appIds = AchievementCatalog.all.map { it.id }.toSet()
        assertEquals(appIds, PlayGamesIds.achievements.keys)
        assertEquals(PlayGamesIds.achievements.size, PlayGamesIds.achievements.values.toSet().size)
        assertTrue(PlayGamesIds.standardAchievementAppIds.all { it in appIds })
    }

    @Test
    fun everyCategoryHasExactlyOneLeaderboard() {
        assertEquals(TriviaCategory.entries.toSet(), PlayGamesIds.categoryLeaderboards.keys)
        assertEquals(
            PlayGamesIds.categoryLeaderboards.size,
            PlayGamesIds.categoryLeaderboards.values.toSet().size,
        )
        assertTrue(PlayGamesIds.dailyLeaderboard !in PlayGamesIds.categoryLeaderboards.values)
    }
}

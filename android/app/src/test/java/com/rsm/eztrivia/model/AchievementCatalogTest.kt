package com.rsm.eztrivia.model

import com.rsm.eztrivia.data.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Test

class AchievementCatalogTest {
    @Test
    fun progressUsesDurablePlayerFacts() {
        val state = PlayerState(
            totalRoundsCompleted = 50,
            quickPlayRoundsCompleted = 10,
            lifetimePointsByCategory = mapOf("history" to 12_000),
            playedCategoryRawValues = TriviaCategory.entries.take(14).mapTo(mutableSetOf()) { it.wireName },
            perfectDifficultyRawValues = setOf(TriviaDifficulty.HARD.wireName),
        )
        val progress = AchievementCatalog.progress(state)

        assertEquals(100, progress["EZTrivia.achievement.first_round"])
        assertEquals(100, progress["EZTrivia.achievement.rounds_50"])
        assertEquals(50, progress["EZTrivia.achievement.rounds_100"])
        assertEquals(100, progress["EZTrivia.local.quick_play_10"])
        assertEquals(100, progress["EZTrivia.achievement.all_categories_14"])
        assertEquals(87, progress["EZTrivia.local.all_categories_16"])
        assertEquals(100, progress["EZTrivia.achievement.perfect_hard"])
        assertEquals(0, progress["EZTrivia.achievement.perfect_easy"])
        assertEquals(100, progress["EZTrivia.achievement.points_10000"])
        assertEquals(24, progress["EZTrivia.achievement.points_50000"])
    }
}

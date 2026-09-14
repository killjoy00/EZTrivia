package com.rsm.eztrivia.model

import com.rsm.eztrivia.data.DailyResult
import com.rsm.eztrivia.data.FriendChallengeResult
import com.rsm.eztrivia.data.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementCatalogTest {
    @Test
    fun progressUsesDurablePlayerFacts() {
        val friendResults = (1UL..5UL).associate { seed ->
            val code = FriendChallengeCode(seed = seed, targetScore = 5, targetPoints = 800)
            code.attemptId to FriendChallengeResult(
                code = code,
                score = 6,
                total = 10,
                points = 950,
                outcomes = List(10) { it < 6 },
                createdChallenge = false,
                dateMillis = seed.toLong(),
            )
        }
        val dailyResults = (100..129).associateWith { day ->
            DailyResult(
                day = day,
                score = if (day == 129) 10 else 7,
                total = 10,
                points = if (day == 129) DailyChallenge.MAXIMUM_POINTS else 900,
                outcomes = List(10) { index -> day == 129 || index < 7 },
                dateMillis = day.toLong(),
            )
        }
        val state = PlayerState(
            totalRoundsCompleted = 50,
            quickPlayRoundsCompleted = 10,
            dailyResultsByDay = dailyResults,
            friendChallengeResultsByAttemptId = friendResults,
            lifetimePointsByCategory = mapOf("history" to 12_000),
            playedCategoryRawValues = TriviaCategory.entries.take(14).mapTo(mutableSetOf()) { it.wireName },
            perfectDifficultyRawValues = setOf(TriviaDifficulty.HARD.wireName),
        )
        val progress = AchievementCatalog.progress(state)

        assertEquals(19, AchievementCatalog.all.size)
        assertEquals(AchievementCatalog.all.size, AchievementCatalog.all.map { it.id }.toSet().size)
        assertEquals(100, progress["EZTrivia.achievement.first_round"])
        assertEquals(100, progress["EZTrivia.achievement.rounds_50"])
        assertEquals(50, progress["EZTrivia.achievement.rounds_100"])
        assertEquals(100, progress["EZTrivia.local.quick_play_10"])
        assertEquals(100, progress["EZTrivia.local.friend_challenges_5"])
        assertEquals(100, progress["EZTrivia.achievement.all_categories_14"])
        assertEquals(87, progress["EZTrivia.local.all_categories_16"])
        assertEquals(100, progress["EZTrivia.achievement.perfect_hard"])
        assertEquals(0, progress["EZTrivia.achievement.perfect_easy"])
        assertEquals(100, progress["EZTrivia.local.daily_perfect"])
        assertEquals(100, progress["EZTrivia.achievement.streak_30"])
        assertEquals(30, progress["EZTrivia.local.streak_100"])
        assertEquals(100, progress["EZTrivia.achievement.points_10000"])
        assertEquals(24, progress["EZTrivia.achievement.points_50000"])
        assertTrue(progress.values.all { it in 0..100 })
    }

    @Test
    fun longestDailyStreakUsesHistoricalBestRun() {
        assertEquals(0, DailyStreak.longest(emptySet()))
        assertEquals(4, DailyStreak.longest(setOf(4, 5, 6, 7, 20, 21)))
        assertEquals(3, DailyStreak.longest(setOf(9, 7, 8)))
    }
}

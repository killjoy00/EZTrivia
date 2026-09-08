package com.rsm.eztrivia.model

import com.rsm.eztrivia.data.PlayerState

data class AchievementDefinition(
    val id: String,
    val title: String,
    val lockedDescription: String,
    val unlockedDescription: String,
    val goal: AchievementGoal,
)

sealed interface AchievementGoal {
    data class Rounds(val target: Int) : AchievementGoal
    data class QuickRounds(val target: Int) : AchievementGoal
    data class Perfect(val difficulty: TriviaDifficulty) : AchievementGoal
    data class Categories(val target: Int) : AchievementGoal
    data class LifetimePoints(val target: Int) : AchievementGoal
}

/**
 * Achievement facts Android can compute today without a network service.
 *
 * IDs deliberately match iOS where the achievement already exists. Play Games
 * can therefore sync these same facts later without migrating local progress.
 * Daily- and Friend-specific badges stay out of this list until those modes are
 * actually present on Android; showing permanently-zero badges would imply a
 * feature the client cannot yet play.
 */
object AchievementCatalog {
    val all: List<AchievementDefinition> = listOf(
        AchievementDefinition(
            id = "EZTrivia.achievement.first_round",
            title = "First Round",
            lockedDescription = "Complete any round.",
            unlockedDescription = "You completed your first round.",
            goal = AchievementGoal.Rounds(1),
        ),
        AchievementDefinition(
            id = "EZTrivia.achievement.perfect_easy",
            title = "Easy Does It",
            lockedDescription = "Earn a perfect score on an Easy category round.",
            unlockedDescription = "You earned a perfect Easy score.",
            goal = AchievementGoal.Perfect(TriviaDifficulty.EASY),
        ),
        AchievementDefinition(
            id = "EZTrivia.achievement.perfect_medium",
            title = "Perfectly Balanced",
            lockedDescription = "Earn a perfect score on a Medium category round.",
            unlockedDescription = "You earned a perfect Medium score.",
            goal = AchievementGoal.Perfect(TriviaDifficulty.MEDIUM),
        ),
        AchievementDefinition(
            id = "EZTrivia.achievement.perfect_hard",
            title = "Hard to Beat",
            lockedDescription = "Earn a perfect score on a Hard category round.",
            unlockedDescription = "You earned a perfect Hard score.",
            goal = AchievementGoal.Perfect(TriviaDifficulty.HARD),
        ),
        AchievementDefinition(
            id = "EZTrivia.achievement.all_categories",
            title = "A Little of Everything",
            lockedDescription = "Play twelve different categories.",
            unlockedDescription = "You played twelve different categories.",
            goal = AchievementGoal.Categories(12),
        ),
        AchievementDefinition(
            id = "EZTrivia.achievement.all_categories_14",
            title = "Full Spectrum",
            lockedDescription = "Play fourteen different categories.",
            unlockedDescription = "You played fourteen different categories.",
            goal = AchievementGoal.Categories(14),
        ),
        AchievementDefinition(
            id = "EZTrivia.achievement.rounds_10",
            title = "Getting Warmed Up",
            lockedDescription = "Complete ten rounds.",
            unlockedDescription = "You completed ten rounds.",
            goal = AchievementGoal.Rounds(10),
        ),
        AchievementDefinition(
            id = "EZTrivia.achievement.rounds_50",
            title = "Trivia Regular",
            lockedDescription = "Complete fifty rounds.",
            unlockedDescription = "You completed fifty rounds.",
            goal = AchievementGoal.Rounds(50),
        ),
        AchievementDefinition(
            id = "EZTrivia.achievement.rounds_100",
            title = "Century Club",
            lockedDescription = "Complete one hundred rounds.",
            unlockedDescription = "You completed one hundred rounds.",
            goal = AchievementGoal.Rounds(100),
        ),
        AchievementDefinition(
            id = "EZTrivia.achievement.points_10000",
            title = "Five Figures",
            lockedDescription = "Earn 10,000 lifetime category points.",
            unlockedDescription = "You earned 10,000 lifetime category points.",
            goal = AchievementGoal.LifetimePoints(10_000),
        ),
        AchievementDefinition(
            id = "EZTrivia.achievement.points_50000",
            title = "Point Collector",
            lockedDescription = "Earn 50,000 lifetime category points.",
            unlockedDescription = "You earned 50,000 lifetime category points.",
            goal = AchievementGoal.LifetimePoints(50_000),
        ),
        AchievementDefinition(
            id = "EZTrivia.local.all_categories_16",
            title = "The Whole Board",
            lockedDescription = "Play all sixteen trivia categories.",
            unlockedDescription = "You played every category in EZ Trivia.",
            goal = AchievementGoal.Categories(16),
        ),
        AchievementDefinition(
            id = "EZTrivia.local.quick_play_1",
            title = "Shuffle Up",
            lockedDescription = "Complete your first Quick Play round.",
            unlockedDescription = "You completed your first Quick Play round.",
            goal = AchievementGoal.QuickRounds(1),
        ),
        AchievementDefinition(
            id = "EZTrivia.local.quick_play_10",
            title = "Mix Master",
            lockedDescription = "Complete ten Quick Play rounds.",
            unlockedDescription = "You completed ten Quick Play rounds.",
            goal = AchievementGoal.QuickRounds(10),
        ),
    )

    fun progress(state: PlayerState): Map<String, Int> =
        all.associate { achievement -> achievement.id to progress(achievement, state) }

    fun progress(achievement: AchievementDefinition, state: PlayerState): Int =
        when (val goal = achievement.goal) {
            is AchievementGoal.Rounds -> percentage(state.totalRoundsCompleted, goal.target)
            is AchievementGoal.QuickRounds -> percentage(state.quickPlayRoundsCompleted, goal.target)
            is AchievementGoal.Perfect -> if (goal.difficulty.wireName in state.perfectDifficultyRawValues) 100 else 0
            is AchievementGoal.Categories -> percentage(state.playedCategoryRawValues.size, goal.target)
            is AchievementGoal.LifetimePoints -> percentage(state.lifetimePointsTotal, goal.target)
        }

    private fun percentage(value: Int, target: Int): Int {
        if (target <= 0) return 100
        return ((value.toLong() * 100L) / target.toLong()).coerceIn(0L, 100L).toInt()
    }
}

package com.rsm.eztrivia.playgames

import com.rsm.eztrivia.model.TriviaCategory

/** The same selector order as iOS GameCenterBoard: Daily first, then categories with configured resource IDs. */
data class PlayGamesBoard(
    val id: String,
    val title: String,
    val isDaily: Boolean,
) {
    companion object {
        val daily = PlayGamesBoard(
            id = PlayGamesIds.dailyLeaderboard,
            title = "Daily Challenge",
            isDaily = true,
        )
        val all = listOf(daily) + TriviaCategory.entries.mapNotNull { category ->
            val id = PlayGamesIds.categoryLeaderboards[category] ?: return@mapNotNull null
            PlayGamesBoard(
                id = id,
                title = category.title,
                isDaily = false,
            )
        }
    }
}

data class PlayGamesRankRow(
    val playerId: String,
    val rank: Long,
    val playerName: String,
    val formattedScore: String,
)

data class PlayGamesLeaderboardSnapshot(
    val localPlayer: PlayGamesRankRow?,
    val leaders: List<PlayGamesRankRow>,
    val totalPlayerCount: Long?,
    /** Top scores are valid, but the separate personal-rank lookup failed. */
    val personalRankUnavailable: Boolean = false,
)

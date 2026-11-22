package com.rsm.eztrivia.playgames

import com.rsm.eztrivia.model.TriviaCategory

/** The same selector order as iOS GameCenterBoard: Daily first, then all 16 categories. */
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
        val all = listOf(daily) + TriviaCategory.entries.map { category ->
            PlayGamesBoard(
                id = requireNotNull(PlayGamesIds.categoryLeaderboards[category]),
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
)

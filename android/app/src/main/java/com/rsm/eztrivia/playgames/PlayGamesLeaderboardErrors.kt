package com.rsm.eztrivia.playgames

import com.google.android.gms.games.GamesClientStatusCodes

/** Player-facing explanations for Play Games score retrieval errors. */
object PlayGamesLeaderboardErrors {
    fun messageForStatus(statusCode: Int): String = when (statusCode) {
        GamesClientStatusCodes.NETWORK_ERROR_NO_DATA ->
            "Google Play Games couldn't retrieve rankings (26504), and no cached scores were available. " +
                "The leaderboard may be new or the Play Games service may be temporarily unavailable. " +
                "Try again or open full Play Games."
        GamesClientStatusCodes.NETWORK_ERROR_OPERATION_FAILED ->
            "Google Play Games couldn't complete the leaderboard request (26506). " +
                "Try again or open full Play Games."
        else ->
            "Google Play Games couldn't load rankings (code $statusCode). " +
                "Try again or open full Play Games."
    }
}

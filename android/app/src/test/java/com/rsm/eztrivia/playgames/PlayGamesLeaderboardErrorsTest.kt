package com.rsm.eztrivia.playgames

import com.google.android.gms.games.GamesClientStatusCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayGamesLeaderboardErrorsTest {
    @Test
    fun networkErrorWithoutCachedDataHasActionableExplanation() {
        val message = PlayGamesLeaderboardErrors.messageForStatus(
            GamesClientStatusCodes.NETWORK_ERROR_NO_DATA,
        )
        assertTrue(message.contains("26504"))
        assertTrue(message.contains("cached scores"))
        assertTrue(message.contains("Try again"))
        assertTrue(message.contains("full Play Games"))
        assertFalse(message.contains("NETWORK_ERROR_NO_DATA"))
    }

    @Test
    fun transientSubmissionFailureHasSpecificMessage() {
        val message = PlayGamesLeaderboardErrors.messageForStatus(
            GamesClientStatusCodes.NETWORK_ERROR_OPERATION_FAILED,
        )
        assertTrue(message.contains("26506"))
        assertTrue(message.contains("Try again"))
    }

    @Test
    fun unexpectedErrorsRetainStatusForSupport() {
        val message = PlayGamesLeaderboardErrors.messageForStatus(26508)
        assertTrue(message.contains("26508"))
    }

    @Test
    fun missingPersonalRankNeverRequiresDiscardingGlobalScores() {
        val topPlayer = PlayGamesRankRow(
            playerId = "example-player",
            rank = 1L,
            playerName = "Example",
            formattedScore = "100",
        )
        val snapshot = PlayGamesLeaderboardSnapshot(
            localPlayer = null,
            leaders = listOf(topPlayer),
            totalPlayerCount = 1,
            personalRankUnavailable = true,
        )
        assertEquals(listOf(topPlayer), snapshot.leaders)
        assertTrue(snapshot.personalRankUnavailable)
        assertEquals(null, snapshot.localPlayer)
    }
}

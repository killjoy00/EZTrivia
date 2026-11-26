package com.rsm.eztrivia.playgames

import android.app.Activity
import android.util.Log
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.FriendsResolutionRequiredException
import com.google.android.gms.games.leaderboard.LeaderboardScore
import com.google.android.gms.games.leaderboard.LeaderboardVariant
import com.rsm.eztrivia.data.PlayerState
import com.rsm.eztrivia.model.AchievementCatalog
import com.rsm.eztrivia.model.DailyChallenge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

data class PlayGamesConnectionState(
    val isChecking: Boolean = true,
    val isAuthenticated: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Thin PGS v2 bridge. Local PlayerState remains gameplay source of truth; PGS
 * mirrors monotonic achievements and leaderboard scores when platform auth is
 * available. This keeps gameplay fully offline-capable.
 */
class PlayGamesManager(private val activity: Activity) {
    private val signInClient = PlayGames.getGamesSignInClient(activity)
    private val _connection = MutableStateFlow(PlayGamesConnectionState())
    val connection: StateFlow<PlayGamesConnectionState> = _connection.asStateFlow()

    fun refreshAuthentication() {
        _connection.value = _connection.value.copy(isChecking = true, errorMessage = null)
        signInClient.isAuthenticated()
            .addOnCompleteListener(activity) { task ->
                val authenticated = task.isSuccessful && task.result.isAuthenticated
                _connection.value = PlayGamesConnectionState(
                    isChecking = false,
                    isAuthenticated = authenticated,
                    errorMessage = if (task.isSuccessful) null else task.exception?.localizedMessage,
                )
            }
    }

    /** Manual retry for the case where PGS automatic platform authentication failed. */
    fun signIn() {
        _connection.value = _connection.value.copy(isChecking = true, errorMessage = null)
        signInClient.signIn()
            .addOnCompleteListener(activity) { task ->
                val authenticated = task.isSuccessful && task.result.isAuthenticated
                _connection.value = PlayGamesConnectionState(
                    isChecking = false,
                    isAuthenticated = authenticated,
                    errorMessage = when {
                        authenticated -> null
                        task.exception != null -> task.exception?.localizedMessage
                        else -> "Google Play Games sign-in was not completed."
                    },
                )
            }
    }

    /**
     * Replays local monotonic facts into PGS. These operations are idempotent:
     * achievement steps never decrease and larger-is-better leaderboard scores
     * cannot be lowered by a stale submission.
     */
    fun sync(state: PlayerState) {
        if (!_connection.value.isAuthenticated) return

        val achievementsClient = PlayGames.getAchievementsClient(activity)
        AchievementCatalog.progress(state).forEach { (appId, percent) ->
            val playGamesId = PlayGamesIds.achievements[appId] ?: return@forEach
            if (appId in PlayGamesIds.standardAchievementAppIds) {
                if (percent >= 100) achievementsClient.unlock(playGamesId)
            } else if (percent > 0) {
                // Incremental PGS achievements are configured as 100 steps, so
                // the app's existing percentage is the exact server step value.
                achievementsClient.setSteps(playGamesId, percent.coerceIn(1, 100))
            }
        }

        val leaderboardsClient = PlayGames.getLeaderboardsClient(activity)
        PlayGamesIds.categoryLeaderboards.forEach { (category, leaderboardId) ->
            val points = state.lifetimePointsByCategory[category.wireName] ?: 0
            if (points > 0) leaderboardsClient.submitScore(leaderboardId, points.toLong())
        }

        // Never replay historical Daily results into today's daily time span.
        // Only a result earned for the current Daily is eligible for submission.
        state.dailyResult(DailyChallenge.day())?.let { result ->
            leaderboardsClient.submitScore(
                PlayGamesIds.dailyLeaderboard,
                result.points.coerceIn(0, DailyChallenge.MAXIMUM_POINTS).toLong(),
            )
        }
    }

    fun showAchievements() {
        if (!_connection.value.isAuthenticated) return
        PlayGames.getAchievementsClient(activity)
            .getAchievementsIntent()
            .addOnSuccessListener { intent ->
                @Suppress("DEPRECATION")
                activity.startActivityForResult(intent, REQUEST_ACHIEVEMENTS)
            }
            .addOnFailureListener(::recordError)
    }

    fun showLeaderboards() {
        if (!_connection.value.isAuthenticated) return
        PlayGames.getLeaderboardsClient(activity)
            .getAllLeaderboardsIntent()
            .addOnSuccessListener { intent ->
                @Suppress("DEPRECATION")
                activity.startActivityForResult(intent, REQUEST_LEADERBOARDS)
            }
            .addOnFailureListener(::recordError)
    }


    /**
     * Native rank data for the in-app Leaderboards screen. Mirrors the iOS
     * global/friends reader without opening a second Play Games Activity.
     * The platform owns identity and standings; we never synthesize ranks.
     *
     * Each Google score buffer must be fully copied and released before the
     * coroutine returns to Compose.
     */
    suspend fun loadLeaderboard(
        board: PlayGamesBoard,
        friendsOnly: Boolean,
        forceReload: Boolean = false,
    ): PlayGamesLeaderboardSnapshot {
        check(_connection.value.isAuthenticated) { "Connect Google Play Games to view rankings." }
        val client = PlayGames.getLeaderboardsClient(activity)
        val span = if (board.isDaily) LeaderboardVariant.TIME_SPAN_DAILY else LeaderboardVariant.TIME_SPAN_ALL_TIME
        val collection = if (friendsOnly) LeaderboardVariant.COLLECTION_FRIENDS else LeaderboardVariant.COLLECTION_PUBLIC

        val loaded = try {
            client.loadTopScores(board.id, span, collection, 10, forceReload).await().get()
                ?: error("This leaderboard is not available yet.")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // 26504 means Google could not obtain fresh scores and had no cache;
            // it is NOT proof that a published board contains zero players.
            Log.w(TAG, "Top rankings unavailable: board=${board.title}, friends=$friendsOnly", error)
            throw error
        }
        val leaders: List<PlayGamesRankRow>
        val playerCount: Long?
        try {
            val scores = loaded.scores
            leaders = (0 until scores.count).map { index ->
                scores.get(index).toRankRow()
            }
            playerCount = loaded.leaderboard?.variants
                ?.firstOrNull { it.timeSpan == span && it.collection == collection }
                ?.numScores?.takeIf { it >= 0L }
        } finally {
            loaded.release()
        }

        // Personal rank is supplementary. Google's own samples report 26504
        // from this particular API even after public rankings loaded. Do not
        // throw away valid global results because this second request failed.
        var personalRankUnavailable = false
        val local = try {
            client.loadCurrentPlayerLeaderboardScore(board.id, span, collection)
                .await().get()?.toRankRow()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (permission: FriendsResolutionRequiredException) {
            // Preserve the consent flow for the Friends collection.
            throw permission
        } catch (error: Exception) {
            personalRankUnavailable = true
            Log.w(TAG, "Personal leaderboard rank unavailable: board=${board.title}, friends=$friendsOnly", error)
            null
        }
        return PlayGamesLeaderboardSnapshot(
            localPlayer = local,
            leaders = leaders,
            totalPlayerCount = playerCount,
            personalRankUnavailable = personalRankUnavailable,
        )
    }

    private fun LeaderboardScore.toRankRow(): PlayGamesRankRow = PlayGamesRankRow(
        playerId = scoreHolder?.playerId.orEmpty(),
        rank = rank,
        playerName = scoreHolderDisplayName,
        formattedScore = displayScore,
    )

    private fun recordError(error: Exception) {
        _connection.value = _connection.value.copy(errorMessage = error.localizedMessage)
    }

    private companion object {
        const val TAG = "EZTriviaLeaderboards"
        const val REQUEST_ACHIEVEMENTS = 7101
        const val REQUEST_LEADERBOARDS = 7102
    }
}

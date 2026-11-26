package com.rsm.eztrivia

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.rsm.eztrivia.data.AppSettingsStore
import com.rsm.eztrivia.data.PlayerStateStore
import com.rsm.eztrivia.data.QuestionCatalog
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.leaderboard.LeaderboardVariant
import com.rsm.eztrivia.data.DailyResult
import com.rsm.eztrivia.model.DailyChallenge
import com.rsm.eztrivia.model.TriviaQuestion
import com.rsm.eztrivia.playgames.PlayGamesIds
import com.rsm.eztrivia.ui.DailyChallengeScreen
import com.rsm.eztrivia.ui.EZTriviaTheme

class DailyChallengeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appearanceStore = remember(applicationContext) { AppSettingsStore(applicationContext) }
            val appearance by appearanceStore.state.collectAsState()
            EZTriviaTheme(darkModeEnabled = appearance.darkModeEnabled) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding(),
                    ) {
                        DailyChallengeEntry(onBackToPlay = ::returnToPlay, onViewStandings = ::viewDailyLeaderboard)
                    }
                }
            }
        }
    }

    private fun viewDailyLeaderboard(result: DailyResult) {
        val signIn = PlayGames.getGamesSignInClient(this)
        signIn.isAuthenticated()
            .addOnSuccessListener { authentication ->
                if (authentication.isAuthenticated) {
                    openPlayGamesDailyBoard(result)
                } else {
                    signIn.signIn()
                        .addOnSuccessListener { retry ->
                            if (retry.isAuthenticated) openPlayGamesDailyBoard(result)
                            else showPlayGamesMessage("Sign in to Play Games to view standings.")
                        }
                        .addOnFailureListener { showPlayGamesMessage("Play Games sign-in unavailable.") }
                }
            }
            .addOnFailureListener { showPlayGamesMessage("Play Games sign-in unavailable.") }
    }

    private fun openPlayGamesDailyBoard(result: DailyResult) {
        val client = PlayGames.getLeaderboardsClient(this)
        // A previous day's result must never be submitted into today's Daily period.
        if (result.day == DailyChallenge.day()) {
            client.submitScore(
                PlayGamesIds.dailyLeaderboard,
                result.points.coerceIn(0, DailyChallenge.MAXIMUM_POINTS).toLong(),
            )
        }
        client.getLeaderboardIntent(PlayGamesIds.dailyLeaderboard, LeaderboardVariant.TIME_SPAN_DAILY)
            .addOnSuccessListener { intent ->
                // PGS requires startActivityForResult to validate the calling game.
                @Suppress("DEPRECATION")
                startActivityForResult(intent, 7103)
            }
            .addOnFailureListener { showPlayGamesMessage("Daily standings are not available yet.") }
    }

    private fun showPlayGamesMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun returnToPlay() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        finish()
    }
}

@Composable
private fun DailyChallengeEntry(
    onBackToPlay: () -> Unit,
    onViewStandings: (DailyResult) -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context.applicationContext) { PlayerStateStore(context.applicationContext) }
    val playerState by store.state.collectAsState()
    val catalogResult by produceState<Result<List<TriviaQuestion>>?>(initialValue = null) {
        value = runCatching { QuestionCatalog.load(context) }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val result = catalogResult) {
            null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> result.fold(
                onSuccess = { catalog ->
                    DailyChallengeScreen(
                        catalog = catalog,
                        playerState = playerState,
                        store = store,
                        onBack = onBackToPlay,
                        onViewStandings = onViewStandings,
                    )
                },
                onFailure = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Text("Daily Challenge unavailable")
                    }
                },
            )
        }
    }
}
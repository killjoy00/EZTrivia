package com.rsm.eztrivia.ui

import android.app.PendingIntent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.games.FriendsResolutionRequiredException
import com.rsm.eztrivia.MainActivity
import com.rsm.eztrivia.playgames.PlayGamesBoard
import com.rsm.eztrivia.playgames.PlayGamesLeaderboardErrors
import com.rsm.eztrivia.playgames.PlayGamesLeaderboardSnapshot
import com.rsm.eztrivia.playgames.PlayGamesRankRow
import java.text.NumberFormat
import kotlinx.coroutines.CancellationException

/**
 * Android counterpart to GameCenterLeaderboardsView: same Daily-first board
 * selection, Global/Friends scopes, your rank, top ten and native fallback.
 * It reads real Play Games data (never a local/fabricated ranking).
 */
@Composable
fun PlayGamesLeaderboardsScreen(
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onSettings: () -> Unit,
) {
    val manager = (LocalActivity.current as? MainActivity)?.playGamesManager
    val connection = manager?.connection?.collectAsState()?.value
    var board by remember { mutableStateOf(PlayGamesBoard.daily) }
    var friendsOnly by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var refreshRevision by remember { mutableIntStateOf(0) }
    // "Try Again" should allow Play Games to use cached scores; only an
    // explicit "Refresh rankings" requests fresh network data.
    var forceReloadNext by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf<PlayGamesLeaderboardSnapshot?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var friendsPermission by remember { mutableStateOf<PendingIntent?>(null) }

    val askFriendsAccess = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) {
        // A denied or canceled consent must not produce invented friends rows.
        // Reload after returning so Google Play Games decides what's available.
        forceReloadNext = false
        refreshRevision++
    }

    LaunchedEffect(manager, connection?.isAuthenticated, board, friendsOnly, refreshRevision) {
        val forceReload = forceReloadNext
        forceReloadNext = false
        snapshot = null
        errorMessage = null
        friendsPermission = null
        if (manager == null || connection?.isAuthenticated != true) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        try {
            snapshot = manager.loadLeaderboard(
                board = board,
                friendsOnly = friendsOnly,
                forceReload = forceReload,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (permission: FriendsResolutionRequiredException) {
            friendsPermission = permission.resolution
            errorMessage = "Allow EZ Trivia to see your Play Games friends to view their scores."
        } catch (error: Exception) {
            errorMessage = if (error is ApiException) {
                PlayGamesLeaderboardErrors.messageForStatus(error.statusCode)
            } else {
                error.localizedMessage ?: "Couldn’t load rankings from Google Play Games."
            }
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        bottomBar = {
            EZTriviaBottomBar(
                selected = AppSection.SCORES,
                onPlay = onPlay,
                onScores = onBack,
                onSettings = onSettings,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("‹ Scores") }
                    Spacer(modifier = Modifier.weight(1f))
                    if (connection?.isAuthenticated == true) {
                        TextButton(onClick = { manager?.showLeaderboards() }) {
                            Text("Open full Play Games")
                        }
                    }
                }
            }
            item {
                Text("Leaderboards", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Leaderboard", fontWeight = FontWeight.SemiBold)
                        androidx.compose.foundation.layout.Box {
                            TextButton(onClick = { menuExpanded = true }) {
                                Text(board.title)
                                Text("  ▾")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                PlayGamesBoard.all.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.title) },
                                        onClick = {
                                            forceReloadNext = false
                                            board = option
                                            menuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                        Text("Players", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !friendsOnly,
                                onClick = { forceReloadNext = false; friendsOnly = false },
                                label = { Text("Global") },
                            )
                            FilterChip(
                                selected = friendsOnly,
                                onClick = { forceReloadNext = false; friendsOnly = true },
                                label = { Text("Friends") },
                            )
                        }
                    }
                }
            }

            when {
                connection?.isChecking == true -> {
                    item { Text("Checking your Google Play Games profile…") }
                }
                connection?.isAuthenticated != true -> {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("Google Play Games unavailable", fontWeight = FontWeight.SemiBold)
                                Text("Sign in to Google Play Games, then return here to see rankings.")
                                Button(onClick = { manager?.signIn() }, enabled = manager != null) {
                                    Text("Connect Google Play Games")
                                }
                            }
                        }
                    }
                }
                isLoading -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator()
                            Text("  Loading rankings…")
                        }
                    }
                }
                errorMessage != null -> {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("Couldn’t load rankings", fontWeight = FontWeight.SemiBold)
                                Text(errorMessage.orEmpty())
                                if (friendsPermission != null) {
                                    Button(onClick = {
                                        friendsPermission?.let { pending ->
                                            askFriendsAccess.launch(
                                                IntentSenderRequest.Builder(pending.intentSender).build(),
                                            )
                                        }
                                    }) { Text("Allow friends access") }
                                }
                                TextButton(onClick = {
                                    forceReloadNext = false
                                    refreshRevision++
                                }) { Text("Try Again") }
                                TextButton(onClick = { manager?.showLeaderboards() }) {
                                    Text("Open full Play Games")
                                }
                            }
                        }
                    }
                }
                snapshot != null -> {
                    val loaded = snapshot!!
                    if (loaded.personalRankUnavailable) {
                        item {
                            Text(
                                "Your rank couldn't be retrieved right now. " +
                                    "Any rankings Google returned are still shown below.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (loaded.localPlayer != null) {
                        item { Text("Your rank", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                LeaderboardRankRow(loaded.localPlayer, isLocalPlayer = true)
                            }
                        }
                    }
                    item {
                        Text(
                            if (friendsOnly) "Friends" else "Top players",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            if (loaded.leaders.isEmpty()) {
                                Text(
                                    if (friendsOnly) "No Play Games friends have posted a score here yet."
                                    else "No scores yet. Be the first player to post a score.",
                                    modifier = Modifier.padding(16.dp),
                                )
                            } else {
                                Column {
                                    loaded.leaders.forEachIndexed { index, entry ->
                                        if (index > 0) HorizontalDivider()
                                        LeaderboardRankRow(
                                            entry,
                                            isLocalPlayer = entry.playerId.isNotBlank() &&
                                                entry.playerId == loaded.localPlayer?.playerId,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    loaded.totalPlayerCount?.let { total ->
                        item {
                            Text(
                                "${NumberFormat.getIntegerInstance().format(total)} ranked players",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    item {
                        TextButton(onClick = {
                            forceReloadNext = true
                            refreshRevision++
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Refresh rankings")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRankRow(entry: PlayGamesRankRow, isLocalPlayer: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (entry.rank > 0) "#${entry.rank}" else "—",
            modifier = Modifier.weight(0.23f),
            fontWeight = FontWeight.Bold,
            color = if (isLocalPlayer) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            entry.playerName,
            modifier = Modifier.weight(0.95f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (isLocalPlayer) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(entry.formattedScore, fontWeight = FontWeight.Bold)
    }
}

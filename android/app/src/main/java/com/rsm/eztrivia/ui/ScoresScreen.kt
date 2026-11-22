package com.rsm.eztrivia.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rsm.eztrivia.MainActivity
import com.rsm.eztrivia.ads.AdBanner
import com.rsm.eztrivia.data.PlayerState
import com.rsm.eztrivia.model.AchievementCatalog
import com.rsm.eztrivia.model.AchievementDefinition
import com.rsm.eztrivia.model.TriviaCategory
import com.rsm.eztrivia.model.TriviaDifficulty
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

enum class AppSection { PLAY, SCORES, SETTINGS }

@Composable
fun EZTriviaBottomBar(
    selected: AppSection,
    onPlay: () -> Unit,
    onScores: () -> Unit,
    onSettings: () -> Unit,
) {
    Column {
        if (selected == AppSection.PLAY) {
            AdBanner()
        }
        NavigationBar {
            NavigationBarItem(
                selected = selected == AppSection.PLAY,
                onClick = onPlay,
                icon = { Text("▶", modifier = Modifier.clearAndSetSemantics { }) },
                label = { Text("Play") },
            )
            NavigationBarItem(
                selected = selected == AppSection.SCORES,
                onClick = onScores,
                icon = { Text("★", modifier = Modifier.clearAndSetSemantics { }) },
                label = { Text("Scores") },
            )
            NavigationBarItem(
                selected = selected == AppSection.SETTINGS,
                onClick = onSettings,
                icon = { Text("⚙", modifier = Modifier.clearAndSetSemantics { }) },
                label = { Text("Settings") },
            )
        }
    }
}

/**
 * Android follows the iOS LeaderboardView section hierarchy rather than treating
 * scores as a separate dashboard. Platform-specific labels are intentional:
 * Game Center on iOS, Play Games on Android.
 */
@Composable
fun ScoresScreen(
    playerState: PlayerState,
    catalogQuestionIds: Set<String>,
    onPlay: () -> Unit,
    onSettings: () -> Unit,
    onLeaderboards: () -> Unit,
    onAchievements: () -> Unit,
    onClearRecent: () -> Unit,
) {
    var showAllCategoryRounds by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    val manager = (LocalActivity.current as? MainActivity)?.playGamesManager
    val connection = manager?.connection?.collectAsState()?.value
    val answeredCount = playerState.completedQuestionIds.count { it in catalogQuestionIds }
    val achievementProgress = AchievementCatalog.progress(playerState)
    val completedAchievements = achievementProgress.values.count { it >= 100 }
    val categoryRounds = if (showAllCategoryRounds) {
        playerState.recentCategoryResults
    } else {
        playerState.recentCategoryResults.take(10)
    }
    val lifetime = playerState.lifetimePointsByCategory.mapNotNull { (raw, points) ->
        runCatching { TriviaCategory.fromWireName(raw) }.getOrNull()?.let { it to points }
    }.filter { it.second > 0 }.sortedByDescending { it.second }

    Scaffold(
        bottomBar = {
            EZTriviaBottomBar(
                selected = AppSection.SCORES,
                onPlay = onPlay,
                onScores = {},
                onSettings = onSettings,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Scores", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    if (playerState.recentCategoryResults.isNotEmpty()) {
                        TextButton(onClick = { showClearConfirmation = true }) { Text("Clear") }
                    }
                }
            }

            item { SectionHeading("Google Play Games") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        ScoresActionRow("≡", "Leaderboards", onLeaderboards)
                        HorizontalDivider()
                        ScoresActionRow(
                            "★",
                            "Achievements",
                            onAchievements,
                            detail = "$completedAchievements/${AchievementCatalog.all.size}",
                        )
                        HorizontalDivider()
                        if (connection?.isAuthenticated == true) {
                            ScoresActionRow("♧", "Open full Play Games", onClick = { manager?.showLeaderboards() })
                        } else {
                            Text(
                                if (connection?.isChecking == true) "Checking Google Play Games sign-in…"
                                else "Google Play Games is not signed in.",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (connection?.isChecking == false) {
                                TextButton(onClick = { manager?.signIn() }) { Text("Connect Google Play Games") }
                            }
                        }
                    }
                }
            }

            item { SectionHeading("Question coverage") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("✓  Questions answered", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                "${number(answeredCount)} / ${number(catalogQuestionIds.size)}",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LinearProgressIndicator(
                            progress = {
                                if (catalogQuestionIds.isEmpty()) 0f
                                else answeredCount.toFloat() / catalogQuestionIds.size.toFloat()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                Text(
                    "Counts each question once after you submit an answer. Coverage syncs with Google Play Games when Saved Games is available.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Match iOS: Daily/Friend histories remain recorded, but their
            // dedicated modes display them instead of expanding this screen.
            if (playerState.recentCategoryResults.isEmpty() && playerState.quickPlayResults.isEmpty()) {
                item {
                    Card {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("No rounds yet", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Finish a category round or Quick Play and it will appear here.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (playerState.recentCategoryResults.isNotEmpty()) {
                item { SectionHeading("Recent category rounds") }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            categoryRounds.forEachIndexed { index, result ->
                                if (index > 0) HorizontalDivider()
                                val category = runCatching { TriviaCategory.fromWireName(result.category) }.getOrNull()
                                val difficulty = runCatching { TriviaDifficulty.fromWireName(result.difficulty) }.getOrNull()
                                CompactScoreRow(
                                    icon = category?.let(::categorySymbol) ?: "?",
                                    title = category?.title ?: result.category,
                                    subtitle = listOfNotNull(difficulty?.title, formatDate(result.dateMillis)).joinToString(" · "),
                                    score = "${result.score}/${result.total}",
                                )
                            }
                            if (playerState.recentCategoryResults.size > 10) {
                                HorizontalDivider()
                                TextButton(
                                    onClick = { showAllCategoryRounds = !showAllCategoryRounds },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(if (showAllCategoryRounds) "Show less" else "Show more") }
                            }
                        }
                    }
                }
            }
            if (playerState.quickPlayResults.isNotEmpty()) {
                item { SectionHeading("Recent Quick Play") }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            playerState.quickPlayResults.take(10).forEachIndexed { index, result ->
                                if (index > 0) HorizontalDivider()
                                CompactScoreRow(
                                    icon = "⇄",
                                    title = "Quick Play",
                                    subtitle = "${formatDate(result.dateMillis)} · ${number(result.points)} pts",
                                    score = "${result.score}/${result.total}",
                                    iconColor = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            if (lifetime.isNotEmpty()) {
                item { SectionHeading("Lifetime points") }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            lifetime.forEachIndexed { index, (category, points) ->
                                if (index > 0) HorizontalDivider()
                                CompactScoreRow(
                                    icon = categorySymbol(category),
                                    title = category.title,
                                    score = number(points),
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        "Harder questions are worth more. These totals are what the category leaderboards rank.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear recent rounds?") },
            text = {
                Text("Recent category rounds and the seen-question cycle will be cleared. Lifetime points, question coverage, Daily and Friend Challenge history, Quick Play history, and achievements will be kept.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirmation = false
                    onClearRecent()
                }) { Text("Clear Recent Rounds") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirmation = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ScoresActionRow(icon: String, title: String, onClick: () -> Unit, detail: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(icon, modifier = Modifier.width(30.dp), color = MaterialTheme.colorScheme.primary)
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (detail != null) {
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CompactScoreRow(
    icon: String,
    title: String,
    score: String,
    subtitle: String? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(icon, modifier = Modifier.width(30.dp), color = iconColor)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(score, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 5.dp, top = 4.dp),
    )
}

private fun number(value: Int): String = NumberFormat.getIntegerInstance().format(value)
private fun formatDate(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

@Composable
fun AchievementsScreen(
    playerState: PlayerState,
    onBack: () -> Unit,
) {
    val progress = AchievementCatalog.progress(playerState)
    val completed = progress.values.count { it >= 100 }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Scores") }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        item {
            Text("Achievements", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("$completed of ${AchievementCatalog.all.size} unlocked", fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(
                        progress = {
                            if (AchievementCatalog.all.isEmpty()) 0f
                            else completed.toFloat() / AchievementCatalog.all.size.toFloat()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Badges unlock automatically as you play.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(AchievementCatalog.all, key = AchievementDefinition::id) { achievement ->
            AchievementRow(achievement, progress[achievement.id] ?: 0)
        }
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "More achievements will appear as new ways to play arrive.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AchievementRow(achievement: AchievementDefinition, progress: Int) {
    val complete = progress >= 100
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(if (complete) "✓" else "☆", fontWeight = FontWeight.Bold)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(achievement.title, fontWeight = FontWeight.Bold)
                Text(
                    if (complete) achievement.unlockedDescription else achievement.lockedDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (complete) "Unlocked" else "$progress%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


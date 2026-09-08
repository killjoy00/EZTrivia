package com.rsm.eztrivia.ui

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rsm.eztrivia.DailyChallengeActivity
import com.rsm.eztrivia.FriendChallengeActivity
import com.rsm.eztrivia.data.CategoryRoundResult
import com.rsm.eztrivia.data.DailyResult
import com.rsm.eztrivia.data.FriendChallengeResult
import com.rsm.eztrivia.data.PlayerState
import com.rsm.eztrivia.data.QuickPlayResult
import com.rsm.eztrivia.model.AchievementCatalog
import com.rsm.eztrivia.model.AchievementDefinition
import com.rsm.eztrivia.model.DailyChallenge
import com.rsm.eztrivia.model.TriviaCategory
import com.rsm.eztrivia.model.TriviaDifficulty
import java.text.DateFormat
import java.util.Date

enum class AppSection { PLAY, SCORES, SETTINGS }

@Composable
fun EZTriviaBottomBar(
    selected: AppSection,
    onPlay: () -> Unit,
    onScores: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    NavigationBar {
        NavigationBarItem(
            selected = selected == AppSection.PLAY,
            onClick = onPlay,
            icon = { Text("▶", modifier = Modifier.clearAndSetSemantics { }) },
            label = { Text("Play") },
        )
        NavigationBarItem(
            selected = false,
            onClick = {
                context.startActivity(Intent(context, DailyChallengeActivity::class.java))
            },
            icon = { Text("☀", modifier = Modifier.clearAndSetSemantics { }) },
            label = { Text("Daily") },
        )
        NavigationBarItem(
            selected = false,
            onClick = {
                context.startActivity(Intent(context, FriendChallengeActivity::class.java))
            },
            icon = { Text("↔", modifier = Modifier.clearAndSetSemantics { }) },
            label = { Text("Friends") },
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

@Composable
fun ScoresScreen(
    playerState: PlayerState,
    catalogQuestionIds: Set<String>,
    onPlay: () -> Unit,
    onSettings: () -> Unit,
    onAchievements: () -> Unit,
    onClearRecent: () -> Unit,
) {
    var showAllCategoryRounds by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }

    val answeredCount = playerState.completedQuestionIds.count { it in catalogQuestionIds }
    val totalQuestions = catalogQuestionIds.size
    val achievementProgress = AchievementCatalog.progress(playerState)
    val completedAchievements = achievementProgress.values.count { it >= 100 }
    val categoryResults = if (showAllCategoryRounds) {
        playerState.recentCategoryResults
    } else {
        playerState.recentCategoryResults.take(10)
    }
    val dailyResults = playerState.dailyResultsByDay.values
        .sortedByDescending(DailyResult::day)
        .take(10)
    val friendResults = playerState.friendChallengeResultsByAttemptId.values
        .sortedByDescending(FriendChallengeResult::dateMillis)
        .take(10)

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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Scores", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Your progress, recent rounds, and lifetime totals.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                CoverageCard(answered = answeredCount, total = totalQuestions)
            }

            item {
                AchievementSummaryCard(
                    completed = completedAchievements,
                    total = AchievementCatalog.all.size,
                    onClick = onAchievements,
                )
            }

            if (
                playerState.recentCategoryResults.isEmpty() &&
                playerState.quickPlayResults.isEmpty() &&
                dailyResults.isEmpty() &&
                friendResults.isEmpty()
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("No rounds yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Finish a category round, Quick Play, Daily, or Friend Challenge and it will appear here.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (dailyResults.isNotEmpty()) {
                item { SectionHeading("Recent Daily Challenges") }
                items(dailyResults, key = DailyResult::day) { result ->
                    DailyResultRow(result)
                }
            }

            if (playerState.recentCategoryResults.isNotEmpty()) {
                item { SectionHeading("Recent category rounds") }
                items(categoryResults, key = CategoryRoundResult::id) { result ->
                    CategoryRoundRow(result)
                }
                if (playerState.recentCategoryResults.size > 10) {
                    item {
                        TextButton(
                            onClick = { showAllCategoryRounds = !showAllCategoryRounds },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (showAllCategoryRounds) "Show less" else "Show more")
                        }
                    }
                }
            }

            if (playerState.quickPlayResults.isNotEmpty()) {
                item { SectionHeading("Recent Quick Play") }
                items(playerState.quickPlayResults.take(10), key = QuickPlayResult::id) { result ->
                    QuickPlayRow(result)
                }
            }

            if (friendResults.isNotEmpty()) {
                item { SectionHeading("Recent Friend Challenges") }
                items(friendResults, key = { it.code.attemptId }) { result ->
                    FriendChallengeRow(result)
                }
            }

            val lifetime = playerState.lifetimePointsByCategory
                .mapNotNull { (rawCategory, points) ->
                    runCatching { TriviaCategory.fromWireName(rawCategory) }.getOrNull()?.let { it to points }
                }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }

            if (lifetime.isNotEmpty()) {
                item { SectionHeading("Lifetime points") }
                items(lifetime, key = { it.first.wireName }) { (category, points) ->
                    LifetimePointsRow(category, points)
                }
                item {
                    Text(
                        "Harder questions are worth more. Keep playing to build your totals in every category.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (playerState.recentCategoryResults.isNotEmpty()) {
                item {
                    TextButton(
                        onClick = { showClearConfirmation = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Clear recent category rounds")
                    }
                }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear recent rounds?") },
            text = {
                Text("Recent category rounds and the seen-question cycle will be cleared. Lifetime points, question coverage, Daily history, Quick Play history, Friend Challenge history, and achievements will be kept.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onClearRecent()
                    },
                ) { Text("Clear recent rounds") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CoverageCard(answered: Int, total: Int) {
    val fraction = if (total == 0) 0f else answered.toFloat() / total.toFloat()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Question coverage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "$answered / $total",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Counts each question once after you submit an answer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AchievementSummaryCard(completed: Int, total: Int, onClick: () -> Unit) {
    val fraction = if (total == 0) 0f else completed.toFloat() / total.toFloat()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Achievements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text("$completed / $total", fontWeight = FontWeight.Bold)
                Text("  ›", style = MaterialTheme.typography.titleMedium)
            }
            LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text(
                "Complete rounds, explore categories, and build your lifetime score to unlock badges.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun DailyResultRow(result: DailyResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(modifier = Modifier.size(38.dp), shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("☀", fontWeight = FontWeight.Bold)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Daily #${DailyChallenge.displayNumber(result.day)}", fontWeight = FontWeight.Bold)
                Text(
                    "${formatDate(result.dateMillis)} · ${result.points} pts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("${result.score}/${result.total}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CategoryRoundRow(result: CategoryRoundResult) {
    val category = runCatching { TriviaCategory.fromWireName(result.category) }.getOrNull()
    val difficulty = runCatching { TriviaDifficulty.fromWireName(result.difficulty) }.getOrNull()
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = category?.let { categoryColor(it).copy(alpha = 0.16f) }
                    ?: MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(
                        category?.title?.take(1) ?: "?",
                        fontWeight = FontWeight.Bold,
                        color = category?.let(::categoryColor) ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(category?.title ?: result.category, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(difficulty?.title, formatDate(result.dateMillis)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("${result.score}/${result.total}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuickPlayRow(result: QuickPlayResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(modifier = Modifier.size(38.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("↝", fontWeight = FontWeight.Bold)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Quick Play", fontWeight = FontWeight.Bold)
                Text(
                    "${formatDate(result.dateMillis)} · ${result.points} pts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("${result.score}/${result.total}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FriendChallengeRow(result: FriendChallengeResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(modifier = Modifier.size(38.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("2", fontWeight = FontWeight.Bold)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (result.createdChallenge) "Created challenge" else "Played challenge", fontWeight = FontWeight.Bold)
                Text(
                    "${formatDate(result.dateMillis)} · ${result.points} pts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("${result.score}/${result.total}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LifetimePointsRow(category: TriviaCategory, points: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = categoryColor(category).copy(alpha = 0.16f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(category.title.take(1), color = categoryColor(category), fontWeight = FontWeight.Bold)
                }
            }
            Text(category.title, modifier = Modifier.padding(start = 12.dp))
            Spacer(modifier = Modifier.weight(1f))
            Text(points.toString(), fontWeight = FontWeight.Bold)
        }
    }
}

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

private fun formatDate(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

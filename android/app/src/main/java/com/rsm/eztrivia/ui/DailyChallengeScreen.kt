package com.rsm.eztrivia.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rsm.eztrivia.data.AppSettings
import com.rsm.eztrivia.data.DailyResult
import com.rsm.eztrivia.data.PlayerState
import com.rsm.eztrivia.data.PlayerStateStore
import com.rsm.eztrivia.model.DailyChallenge
import com.rsm.eztrivia.model.DailyStreak
import com.rsm.eztrivia.model.RoundSummary
import com.rsm.eztrivia.model.TriviaEngine
import com.rsm.eztrivia.model.TriviaQuestion
import com.rsm.eztrivia.share.ScoreCardContent
import com.rsm.eztrivia.share.ScoreCardShare
import java.text.NumberFormat
import kotlinx.coroutines.launch

private val dailyTint = Color(0xFFF97316)

@Composable
fun DailyChallengeHomeCard(
    playerState: PlayerState,
    onClick: () -> Unit,
) {
    val today = remember { DailyChallenge.day() }
    val supported = DailyChallenge.isCrossPlatformDay(today)
    val result = playerState.dailyResult(today)
    val streak = playerState.dailyStreak(today)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(16.dp),
                color = dailyTint.copy(alpha = 0.15f),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(if (result == null) "☀" else "✓", color = dailyTint, fontWeight = FontWeight.Bold)
                    Text("#${DailyChallenge.displayNumber(today)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Daily Challenge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        !supported -> "Cross-platform Daily starts with #252 tomorrow."
                        result != null -> "Done — ${result.score}/${result.total}. Back tomorrow."
                        else -> "Ten questions. Everyone plays the same set."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (streak > 0) {
                    Text("🔥 $streak day streak", style = MaterialTheme.typography.labelMedium, color = dailyTint, fontWeight = FontWeight.Bold)
                }
            }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
fun DailyChallengeScreen(
    catalog: List<TriviaQuestion>,
    playerState: PlayerState,
    store: PlayerStateStore,
    onBack: () -> Unit,
) {
    val today = remember { DailyChallenge.day() }
    val settings = rememberAppSettings()
    val scope = rememberCoroutineScope()
    var persistedResult by remember(today) { mutableStateOf<DailyResult?>(null) }
    var localResult by remember(today) { mutableStateOf<DailyResult?>(null) }
    var loaded by remember(today) { mutableStateOf(false) }

    DailyReminderEffect(
        settings = settings,
        playedDays = playerState.dailyResultsByDay.keys,
    )

    LaunchedEffect(today) {
        persistedResult = store.persistedDailyResult(today)
        loaded = true
    }

    val result = localResult ?: playerState.dailyResult(today) ?: persistedResult

    when {
        !DailyChallenge.isCrossPlatformDay(today) -> DailyNotStartedScreen(onBack)
        !loaded -> DailyLoadingScreen()
        result != null -> DailyResultScreen(
            result = result,
            streak = DailyStreak.current(playerState.dailyResultsByDay.keys + result.day, result.day),
            onBack = onBack,
        )
        else -> {
            val questions = remember(today, catalog) { DailyChallenge.challenge(today, catalog) }
            DailyRoundScreen(
                day = today,
                questions = questions,
                settings = settings,
                onAnswer = { question, correct ->
                    scope.launch { store.recordQuestionAnswer(question, correct) }
                },
                onComplete = { engine ->
                    val completed = DailyResult(
                        day = today,
                        score = engine.score,
                        total = engine.questions.size,
                        points = engine.points,
                        outcomes = engine.outcomes,
                        dateMillis = System.currentTimeMillis(),
                    )
                    localResult = completed
                    scope.launch {
                        store.recordDaily(
                            result = completed,
                            categories = engine.questions.mapTo(mutableSetOf()) { it.category },
                        )
                    }
                },
                onExit = onBack,
            )
        }
    }
}

@Composable
private fun DailyRoundScreen(
    day: Int,
    questions: List<TriviaQuestion>,
    settings: AppSettings,
    onAnswer: (TriviaQuestion, Boolean) -> Unit,
    onComplete: (TriviaEngine) -> Unit,
    onExit: () -> Unit,
) {
    val engine = remember(day, questions) { TriviaEngine(questions) }
    val feedback = rememberAppFeedback()
    val view = LocalView.current
    var revision by remember(day, questions) { mutableIntStateOf(0) }
    var showExitConfirmation by remember { mutableStateOf(false) }

    @Suppress("UNUSED_EXPRESSION")
    revision

    BackHandler { showExitConfirmation = true }

    if (engine.isRoundComplete) {
        DailyLoadingScreen()
        return
    }

    val question = engine.currentQuestion ?: return
    val answered = engine.selectedAnswerIndex != null

    fun advanceRound() {
        if (engine.isRoundComplete || engine.selectedAnswerIndex == null) return
        val finishing = engine.currentIndex == engine.questions.lastIndex
        if (finishing) {
            onComplete(engine)
            feedback.roundComplete(settings, view)
        }
        engine.advance()
        revision += 1
    }

    val autoAdvanceRemaining = rememberAutoAdvanceCountdown(
        questionIndex = engine.currentIndex,
        selectedAnswerIndex = engine.selectedAnswerIndex,
        settings = settings,
        onElapsed = ::advanceRound,
    )

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { showExitConfirmation = true }) { Text("Exit") }
                Spacer(modifier = Modifier.weight(1f))
                Text("Daily #${DailyChallenge.displayNumber(day)}", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text("${engine.score} correct", color = dailyTint)
            }
        },
        bottomBar = {
            if (answered) {
                Surface(shadowElevation = 8.dp) {
                    Button(
                        onClick = ::advanceRound,
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                    ) {
                        val base = if (engine.currentIndex == engine.questions.lastIndex) "Finish" else "Next question"
                        Text(roundActionLabel(base, autoAdvanceRemaining))
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (answered) 12.dp else 18.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "QUESTION ${engine.currentIndex + 1} OF ${engine.questions.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    LinearProgressIndicator(
                        progress = { (engine.currentIndex + 1).toFloat() / engine.questions.size.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                        color = dailyTint,
                    )
                }
            }

            question.visual?.let { visual -> item { DailyFlagVisual(visual, answered) } }

            item {
                Text(
                    question.prompt,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = if (question.visual == null) TextAlign.Start else TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().questionHeading(),
                )
            }

            items(question.answers.indices.toList()) { index ->
                DailyAnswerButton(
                    question = question,
                    index = index,
                    selectedIndex = engine.selectedAnswerIndex,
                    onClick = {
                        if (engine.selectedAnswerIndex == null) {
                            val correct = engine.answer(index)
                            feedback.answer(correct, settings, view)
                            onAnswer(question, correct)
                            revision += 1
                        }
                    },
                )
            }

            if (answered) item { DailyExplanationCard(question, engine.selectedAnswerIndex) }
        }
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("Leave today's challenge?") },
            text = { Text("Leaving does not save an attempt. You can restart from question one later today.") },
            confirmButton = { TextButton(onClick = onExit) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { showExitConfirmation = false }) { Text("Keep playing") } },
        )
    }
}

@Composable
private fun DailyAnswerButton(
    question: TriviaQuestion,
    index: Int,
    selectedIndex: Int?,
    onClick: () -> Unit,
) {
    val answered = selectedIndex != null
    val isCorrect = index == question.correctAnswerIndex
    val isSelectedWrong = answered && index == selectedIndex && !isCorrect
    val letter = ('A'.code + index).toChar()
    val border = when {
        answered && isCorrect -> dailyTint
        isSelectedWrong -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val container = when {
        answered && isCorrect -> dailyTint.copy(alpha = 0.13f)
        isSelectedWrong -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surface
    }

    OutlinedButton(
        onClick = onClick,
        enabled = !answered,
        modifier = Modifier
            .fillMaxWidth()
            .answerAccessibility(
                letter = letter,
                answer = question.answers[index],
                answered = answered,
                isCorrect = isCorrect,
                isSelectedWrong = isSelectedWrong,
            ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = container,
            disabledContainerColor = container,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    Text(letter.toString(), fontWeight = FontWeight.Bold)
                }
            }
            Text(question.answers[index], modifier = Modifier.weight(1f), textAlign = TextAlign.Start, fontWeight = FontWeight.SemiBold)
            if (answered && isCorrect) Text("✓", fontWeight = FontWeight.Bold)
            if (isSelectedWrong) Text("×", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DailyExplanationCard(question: TriviaQuestion, selectedIndex: Int?) {
    val correct = selectedIndex == question.correctAnswerIndex
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (correct) "Correct!" else "Good try!",
                style = MaterialTheme.typography.titleMedium,
                color = if (correct) dailyTint else MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold,
            )
            Text(question.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DailyFlagVisual(visual: String, compact: Boolean) {
    val context = LocalContext.current
    val bitmap = remember(visual) {
        runCatching {
            context.assets.open("flags/$visual.png").use { stream -> BitmapFactory.decodeStream(stream) }
        }.getOrNull()
    } ?: return

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Card {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Flag image for this question",
                modifier = Modifier.height(if (compact) 110.dp else 175.dp).padding(8.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun DailyResultScreen(
    result: DailyResult,
    streak: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val shareText = RoundSummary.daily(
        day = DailyChallenge.displayNumber(result.day),
        outcomes = result.outcomes,
        points = result.points,
        streak = streak,
    )
    val shareCard = ScoreCardContent(
        title = "Daily #${DailyChallenge.displayNumber(result.day)}",
        subtitle = "${formatDailyPoints(result.points)} points",
        headline = "${result.score}/${result.total}",
        outcomes = result.outcomes,
        footnote = if (streak > 1) "$streak day streak" else null,
        tintArgb = dailyTint.toArgb(),
    )

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(modifier = Modifier.size(92.dp), shape = CircleShape, color = dailyTint.copy(alpha = 0.14f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(if (streak > 1) "🔥" else "✓", style = MaterialTheme.typography.headlineLarge, color = dailyTint)
            }
        }
        Text(
            "Daily #${DailyChallenge.displayNumber(result.day)} complete",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            "${result.score} / ${result.total}",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = dailyTint,
        )
        Text(RoundSummary.grid(result.outcomes), style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            DailyStat(formatDailyPoints(result.points), "points")
            DailyStat(streak.toString(), "day streak")
        }
        Text(
            if (streak > 1) "Come back tomorrow to keep the streak going." else "A new set of ten arrives tomorrow.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {
                ScoreCardShare.share(
                    context = context,
                    message = shareText,
                    headline = "I scored ${result.score}/${result.total} on EZ Trivia Daily",
                    card = shareCard,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Share result")
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to categories") }
    }
}

@Composable
private fun DailyStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DailyLoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("Preparing today's questions…")
        }
    }
}

@Composable
private fun DailyNotStartedScreen(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Daily Challenge starts tomorrow", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Android joins the shared Daily with #252 on September 9, 2026. That avoids changing today's already-live iPhone round halfway through the day.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onBack) { Text("Back to categories") }
        }
    }
}

private fun formatDailyPoints(points: Int): String = NumberFormat.getIntegerInstance().format(points)

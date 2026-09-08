package com.rsm.eztrivia.ui

import android.content.Context
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rsm.eztrivia.data.PlayerState
import com.rsm.eztrivia.data.PlayerStateStore
import com.rsm.eztrivia.data.QuestionCatalog
import com.rsm.eztrivia.model.QuestionPicker
import com.rsm.eztrivia.model.RoundSummary
import com.rsm.eztrivia.model.TriviaCategory
import com.rsm.eztrivia.model.TriviaDifficulty
import com.rsm.eztrivia.model.TriviaEngine
import com.rsm.eztrivia.model.TriviaQuestion
import com.rsm.eztrivia.share.ScoreCardContent
import com.rsm.eztrivia.share.ScoreCardShare
import kotlinx.coroutines.launch

private sealed interface AppScreen {
    data object Home : AppScreen
    data object Scores : AppScreen
    data object Achievements : AppScreen
    data class Difficulty(val category: TriviaCategory) : AppScreen
    data class Round(val mode: RoundMode, val questions: List<TriviaQuestion>) : AppScreen
}

private sealed interface RoundMode {
    data object QuickPlay : RoundMode
    data class Category(val category: TriviaCategory, val difficulty: TriviaDifficulty) : RoundMode
}

@Composable
fun EZTriviaApp() {
    val context = LocalContext.current
    val playerStateStore = remember(context.applicationContext) {
        PlayerStateStore(context.applicationContext)
    }
    val playerState by playerStateStore.state.collectAsState()
    val catalogResult by produceState<Result<List<TriviaQuestion>>?>(initialValue = null) {
        value = runCatching { QuestionCatalog.load(context) }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val result = catalogResult) {
            null -> LoadingScreen()
            else -> result.fold(
                onSuccess = { catalog ->
                    TriviaNavigation(
                        catalog = catalog,
                        playerStateStore = playerStateStore,
                        playerState = playerState,
                    )
                },
                onFailure = { error -> CatalogErrorScreen(error.message ?: "Unknown catalog error") },
            )
        }
    }
}

@Composable
private fun TriviaNavigation(
    catalog: List<TriviaQuestion>,
    playerStateStore: PlayerStateStore,
    playerState: PlayerState,
) {
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }
    val scope = rememberCoroutineScope()
    val catalogQuestionIds = remember(catalog) { catalog.mapTo(mutableSetOf()) { it.id } }

    fun markSeen(questions: List<TriviaQuestion>) {
        questions.groupBy { it.category to it.difficulty }.forEach { (key, group) ->
            val (category, difficulty) = key
            scope.launch {
                playerStateStore.markSeen(
                    ids = group.mapTo(mutableSetOf()) { it.id },
                    category = category,
                    difficulty = difficulty,
                    availableCount = QuestionPicker.availableCount(catalog, category, difficulty),
                )
            }
        }
    }

    fun start(mode: RoundMode) {
        val questions = when (mode) {
            RoundMode.QuickPlay -> QuestionPicker.quickPlayRound(
                bank = catalog,
                excludedIds = playerState.allSeenQuestionIds,
            )
            is RoundMode.Category -> QuestionPicker.round(
                bank = catalog,
                category = mode.category,
                difficulty = mode.difficulty,
                excludedIds = playerState.seenQuestions(mode.category, mode.difficulty),
            )
        }
        if (questions.isNotEmpty()) {
            markSeen(questions)
            screen = AppScreen.Round(mode, questions)
        }
    }

    BackHandler(enabled = screen != AppScreen.Home) {
        screen = when (val current = screen) {
            AppScreen.Home -> AppScreen.Home
            AppScreen.Scores -> AppScreen.Home
            AppScreen.Achievements -> AppScreen.Scores
            is AppScreen.Difficulty -> AppScreen.Home
            is AppScreen.Round -> when (val mode = current.mode) {
                RoundMode.QuickPlay -> AppScreen.Home
                is RoundMode.Category -> AppScreen.Difficulty(mode.category)
            }
        }
    }

    when (val current = screen) {
        AppScreen.Home -> HomeScreen(
            catalog = catalog,
            playerState = playerState,
            onQuickPlay = { start(RoundMode.QuickPlay) },
            onCategory = { screen = AppScreen.Difficulty(it) },
            onScores = { screen = AppScreen.Scores },
        )
        AppScreen.Scores -> ScoresScreen(
            playerState = playerState,
            catalogQuestionIds = catalogQuestionIds,
            onPlay = { screen = AppScreen.Home },
            onAchievements = { screen = AppScreen.Achievements },
            onClearRecent = {
                scope.launch { playerStateStore.clearRecentCategoryHistory() }
            },
        )
        AppScreen.Achievements -> AchievementsScreen(
            playerState = playerState,
            onBack = { screen = AppScreen.Scores },
        )
        is AppScreen.Difficulty -> DifficultyScreen(
            category = current.category,
            catalog = catalog,
            onBack = { screen = AppScreen.Home },
            onDifficulty = { difficulty -> start(RoundMode.Category(current.category, difficulty)) },
        )
        is AppScreen.Round -> RoundScreen(
            mode = current.mode,
            questions = current.questions,
            onAnswer = { question, correct ->
                scope.launch { playerStateStore.recordQuestionAnswer(question, correct) }
            },
            onComplete = { mode, engine ->
                scope.launch {
                    when (mode) {
                        RoundMode.QuickPlay -> playerStateStore.recordQuickPlay(
                            score = engine.score,
                            total = engine.questions.size,
                            points = engine.points,
                            outcomes = engine.outcomes,
                            categories = engine.questions.mapTo(mutableSetOf()) { it.category },
                        )
                        is RoundMode.Category -> playerStateStore.recordCategoryRound(
                            category = mode.category,
                            difficulty = mode.difficulty,
                            score = engine.score,
                            total = engine.questions.size,
                            points = engine.points,
                        )
                    }
                }
            },
            onExit = {
                screen = when (val mode = current.mode) {
                    RoundMode.QuickPlay -> AppScreen.Home
                    is RoundMode.Category -> AppScreen.Difficulty(mode.category)
                }
            },
            onPlayAgain = { start(current.mode) },
            onHome = { screen = AppScreen.Home },
        )
    }
}

@Composable
private fun HomeScreen(
    catalog: List<TriviaQuestion>,
    playerState: PlayerState,
    onQuickPlay: () -> Unit,
    onCategory: (TriviaCategory) -> Unit,
    onScores: () -> Unit,
) {
    val catalogQuestionIds = remember(catalog) { catalog.mapTo(mutableSetOf()) { it.id } }
    val answeredCurrentQuestions = playerState.completedQuestionIds.count { it in catalogQuestionIds }

    Scaffold(
        bottomBar = {
            EZTriviaBottomBar(
                selected = AppSection.PLAY,
                onPlay = {},
                onScores = onScores,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Ready to play?",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Ten quick questions. One great score.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            QuickPlayCard(onClick = onQuickPlay)
            PlayerProgressCard(
                playerState = playerState,
                answeredQuestions = answeredCurrentQuestions,
                totalQuestions = catalog.size,
            )

            Text(
                text = "Choose a category",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 155.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                gridItems(TriviaCategory.entries) { category ->
                    CategoryCard(
                        category = category,
                        questionCount = catalog.count { it.category == category },
                        onClick = { onCategory(category) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerProgressCard(
    playerState: PlayerState,
    answeredQuestions: Int,
    totalQuestions: Int,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Your progress", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "$answeredQuestions of $totalQuestions questions answered",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${playerState.totalRoundsCompleted}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text("rounds", style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${playerState.lifetimePointsTotal}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text("points", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun QuickPlayCard(onClick: () -> Unit) {
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
            Box(modifier = Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                Text("10", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Quick Play", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Ten categories. Easy to hard.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun CategoryCard(
    category: TriviaCategory,
    questionCount: Int,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = categoryColor(category).copy(alpha = 0.16f),
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(category.title.take(1), fontWeight = FontWeight.Bold, color = categoryColor(category))
                    }
                }
            }
            Text(category.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                category.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "$questionCount questions",
                style = MaterialTheme.typography.labelSmall,
                color = categoryColor(category),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DifficultyScreen(
    category: TriviaCategory,
    catalog: List<TriviaQuestion>,
    onBack: () -> Unit,
    onDifficulty: (TriviaDifficulty) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Categories") }
            Spacer(modifier = Modifier.weight(1f))
            Text(category.title, fontWeight = FontWeight.Bold)
        }
        Text("Choose your challenge", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Questions are served 10 at a time.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listItems(TriviaDifficulty.entries) { difficulty ->
                val count = QuestionPicker.availableCount(catalog, category, difficulty)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = count > 0) { onDifficulty(difficulty) },
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(difficulty.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                difficulty.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "$count available",
                                style = MaterialTheme.typography.labelMedium,
                                color = categoryColor(category),
                            )
                        }
                        Text("›", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundScreen(
    mode: RoundMode,
    questions: List<TriviaQuestion>,
    onAnswer: (TriviaQuestion, Boolean) -> Unit,
    onComplete: (RoundMode, TriviaEngine) -> Unit,
    onExit: () -> Unit,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit,
) {
    val engine = remember(questions) { TriviaEngine(questions) }
    var revision by remember(questions) { mutableIntStateOf(0) }
    var showExitConfirmation by remember { mutableStateOf(false) }

    @Suppress("UNUSED_EXPRESSION")
    revision

    if (engine.isRoundComplete) {
        ResultScreen(
            mode = mode,
            engine = engine,
            onPlayAgain = onPlayAgain,
            onHome = onHome,
        )
        return
    }

    val question = engine.currentQuestion ?: return
    val answered = engine.selectedAnswerIndex != null
    val tint = when (mode) {
        RoundMode.QuickPlay -> MaterialTheme.colorScheme.primary
        is RoundMode.Category -> categoryColor(mode.category)
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { showExitConfirmation = true }) { Text("Exit") }
                Spacer(modifier = Modifier.weight(1f))
                Text(roundTitle(mode), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text("${engine.score} ✓", color = MaterialTheme.colorScheme.primary)
            }
        },
        bottomBar = {
            if (answered) {
                Surface(shadowElevation = 8.dp) {
                    Button(
                        onClick = {
                            val finishing = engine.currentIndex == engine.questions.lastIndex
                            if (finishing) onComplete(mode, engine)
                            engine.advance()
                            revision += 1
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                    ) {
                        Text(if (engine.currentIndex == engine.questions.lastIndex) "See results" else "Next question")
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp),
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
                        color = tint,
                    )
                }
            }

            question.visual?.let { visual ->
                item { FlagVisual(visual = visual, compact = answered) }
            }

            item {
                Text(
                    text = question.prompt,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = if (question.visual == null) TextAlign.Start else TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            listItems(question.answers.indices.toList()) { index ->
                AnswerButton(
                    question = question,
                    index = index,
                    selectedIndex = engine.selectedAnswerIndex,
                    onClick = {
                        if (engine.selectedAnswerIndex == null) {
                            val correct = engine.answer(index)
                            onAnswer(question, correct)
                            revision += 1
                        }
                    },
                )
            }

            if (answered) {
                item { ExplanationCard(question, engine.selectedAnswerIndex) }
            }
        }
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("Leave this round?") },
            text = { Text("Your current score won't be saved.") },
            confirmButton = { TextButton(onClick = onExit) { Text("Leave round") } },
            dismissButton = { TextButton(onClick = { showExitConfirmation = false }) { Text("Keep playing") } },
        )
    }
}

@Composable
private fun AnswerButton(
    question: TriviaQuestion,
    index: Int,
    selectedIndex: Int?,
    onClick: () -> Unit,
) {
    val answered = selectedIndex != null
    val isCorrect = index == question.correctAnswerIndex
    val isSelectedWrong = answered && index == selectedIndex && !isCorrect
    val border = when {
        answered && isCorrect -> MaterialTheme.colorScheme.primary
        isSelectedWrong -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val container = when {
        answered && isCorrect -> MaterialTheme.colorScheme.primaryContainer
        isSelectedWrong -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surface
    }

    OutlinedButton(
        onClick = onClick,
        enabled = !answered,
        modifier = Modifier.fillMaxWidth(),
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
                    Text(('A'.code + index).toChar().toString(), fontWeight = FontWeight.Bold)
                }
            }
            Text(
                question.answers[index],
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
                fontWeight = FontWeight.SemiBold,
            )
            if (answered && isCorrect) Text("✓", fontWeight = FontWeight.Bold)
            if (isSelectedWrong) Text("×", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ExplanationCard(question: TriviaQuestion, selectedIndex: Int?) {
    val correct = selectedIndex == question.correctAnswerIndex
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (correct) "Correct!" else "Good try!",
                style = MaterialTheme.typography.titleMedium,
                color = if (correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                question.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FlagVisual(visual: String, compact: Boolean) {
    val context = LocalContext.current
    val bitmap = remember(visual) { loadFlagBitmap(context, visual) }
    if (bitmap == null) return

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Card {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Flag image for this question",
                modifier = Modifier
                    .height(if (compact) 110.dp else 175.dp)
                    .padding(8.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private fun loadFlagBitmap(context: Context, visual: String) = runCatching {
    context.assets.open("flags/$visual.png").use { stream -> BitmapFactory.decodeStream(stream) }
}.getOrNull()

@Composable
private fun ResultScreen(
    mode: RoundMode,
    engine: TriviaEngine,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit,
) {
    val context = LocalContext.current
    val total = engine.questions.size.coerceAtLeast(1)
    val ratio = engine.score.toFloat() / total
    val headline = when {
        ratio >= 0.8f -> "Trivia champion!"
        ratio >= 0.5f -> "Nice work!"
        else -> "Keep learning!"
    }
    val message = when {
        ratio >= 0.9f -> "Outstanding — you really know your stuff."
        ratio >= 0.6f -> "A strong round. Can you beat it next time?"
        else -> "Every question is a chance to learn something new."
    }
    val tint = when (mode) {
        RoundMode.QuickPlay -> MaterialTheme.colorScheme.primary
        is RoundMode.Category -> categoryColor(mode.category)
    }
    val shareMessage = when (mode) {
        RoundMode.QuickPlay -> RoundSummary.quickPlay(engine.outcomes, engine.points)
        is RoundMode.Category -> RoundSummary.round(mode.category, mode.difficulty, engine.outcomes)
    }
    val shareHeadline = when (mode) {
        RoundMode.QuickPlay -> "I scored ${engine.score}/${engine.questions.size} on EZ Trivia Quick Play"
        is RoundMode.Category -> RoundSummary.headline(engine.score, engine.questions.size)
    }
    val shareCard = when (mode) {
        RoundMode.QuickPlay -> ScoreCardContent(
            title = "Quick Play",
            subtitle = "Mixed categories",
            headline = "${engine.score}/${engine.questions.size}",
            outcomes = engine.outcomes,
            footnote = "${engine.points} points",
            tintArgb = tint.toArgb(),
        )
        is RoundMode.Category -> ScoreCardContent(
            title = mode.category.title,
            subtitle = mode.difficulty.title,
            headline = "${engine.score}/${engine.questions.size}",
            outcomes = engine.outcomes,
            footnote = null,
            tintArgb = tint.toArgb(),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            headline,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text("You scored", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "${engine.score} / ${engine.questions.size}",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = tint,
        )
        Text("${engine.points} points", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            RoundSummary.grid(engine.outcomes),
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(
            onClick = {
                ScoreCardShare.share(
                    context = context,
                    message = shareMessage,
                    headline = shareHeadline,
                    card = shareCard,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Share result")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onPlayAgain, modifier = Modifier.fillMaxWidth()) {
            Text(if (mode == RoundMode.QuickPlay) "Play another mix" else "Play 10 more")
        }
        TextButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) {
            Text("Back to categories")
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text("Preparing questions…")
        }
    }
}

@Composable
private fun CatalogErrorScreen(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Questions unavailable", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun roundTitle(mode: RoundMode): String = when (mode) {
    RoundMode.QuickPlay -> "Quick Play"
    is RoundMode.Category -> "${mode.category.title} · ${mode.difficulty.title}"
}

fun categoryColor(category: TriviaCategory): Color = when (category) {
    TriviaCategory.FOOTBALL, TriviaCategory.BASKETBALL -> Color(0xFFE8872E)
    TriviaCategory.SOCCER -> Color(0xFF2E8B57)
    TriviaCategory.FLAGS -> Color(0xFF3777D1)
    TriviaCategory.HISTORY -> Color(0xFF8B6F47)
    TriviaCategory.SCIENCE -> Color(0xFF7B4FC6)
    TriviaCategory.MOVIES -> Color(0xFFC94F8A)
    TriviaCategory.TV -> Color(0xFF3B9F91)
    TriviaCategory.GEOGRAPHY -> Color(0xFF258EA6)
    TriviaCategory.MUSIC -> Color(0xFF5651B7)
    TriviaCategory.ANIMALS -> Color(0xFF277F78)
    TriviaCategory.FOOD -> Color(0xFFC94C4C)
    TriviaCategory.LITERATURE -> Color(0xFF5C3DB8)
    TriviaCategory.ART -> Color(0xFFDB5C33)
    TriviaCategory.MYTHOLOGY -> Color(0xFF8C7029)
    TriviaCategory.VIDEO_GAMES -> Color(0xFF2E8C8C)
}

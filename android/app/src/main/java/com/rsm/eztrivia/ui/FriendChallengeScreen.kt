package com.rsm.eztrivia.ui

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rsm.eztrivia.data.AppSettings
import com.rsm.eztrivia.data.FriendChallengeResult
import com.rsm.eztrivia.data.PlayerState
import com.rsm.eztrivia.data.PlayerStateStore
import com.rsm.eztrivia.data.QuestionCatalog
import com.rsm.eztrivia.model.FriendChallenge
import com.rsm.eztrivia.model.FriendChallengeCode
import com.rsm.eztrivia.model.FriendChallengeLink
import com.rsm.eztrivia.model.RoundSummary
import com.rsm.eztrivia.model.TriviaEngine
import com.rsm.eztrivia.model.TriviaQuestion
import com.rsm.eztrivia.share.ScoreCardContent
import com.rsm.eztrivia.share.ScoreCardShare
import java.text.NumberFormat
import kotlin.random.Random
import kotlinx.coroutines.launch

private val friendChallengeTint = Color(0xFF4F46E5)

private sealed interface FriendScreen {
    data class Lobby(val initialInput: String? = null) : FriendScreen
    data class Round(
        val seed: ULong,
        val invitation: FriendChallengeCode?,
        val questions: List<TriviaQuestion>,
    ) : FriendScreen
    data class Result(val result: FriendChallengeResult) : FriendScreen
}

@Composable
fun FriendChallengeApp(
    incomingUrl: String?,
    onIncomingUrlConsumed: () -> Unit,
    onBackToPlay: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context.applicationContext) { PlayerStateStore(context.applicationContext) }
    val settings = rememberAppSettings()
    val playerState by store.state.collectAsState()
    val scope = rememberCoroutineScope()
    val catalogResult by produceState<Result<List<TriviaQuestion>>?>(initialValue = null) {
        value = runCatching { QuestionCatalog.load(context) }
    }
    var screen by remember { mutableStateOf<FriendScreen>(FriendScreen.Lobby()) }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val result = catalogResult) {
            null -> FriendLoadingScreen()
            else -> result.fold(
                onSuccess = { catalog ->
                    fun startRound(seed: ULong, invitation: FriendChallengeCode?) {
                        val questions = FriendChallenge.challenge(seed, catalog)
                        if (questions.size == FriendChallenge.QUESTION_COUNT) {
                            screen = FriendScreen.Round(seed, invitation, questions)
                        }
                    }

                    fun openCode(code: FriendChallengeCode) {
                        val saved = playerState.friendChallengeResult(code)
                        if (saved != null) {
                            screen = FriendScreen.Result(saved)
                        } else {
                            startRound(code.seed, code)
                        }
                    }

                    LaunchedEffect(incomingUrl) {
                        if (!incomingUrl.isNullOrBlank()) {
                            val code = FriendChallengeLink.codeFrom(incomingUrl)
                            if (code != null) {
                                openCode(code)
                            } else {
                                screen = FriendScreen.Lobby(incomingUrl)
                            }
                            onIncomingUrlConsumed()
                        }
                    }

                    when (val current = screen) {
                        is FriendScreen.Lobby -> FriendChallengeLobbyScreen(
                            playerState = playerState,
                            initialInput = current.initialInput,
                            onBack = onBackToPlay,
                            onCreateChallenge = {
                                var seed = Random.nextLong().toULong()
                                while ("v${FriendChallenge.CODE_VERSION}-$seed" in playerState.friendChallengeResultsByAttemptId) {
                                    seed = Random.nextLong().toULong()
                                }
                                startRound(seed, null)
                            },
                            onOpenChallenge = ::openCode,
                        )
                        is FriendScreen.Round -> FriendChallengeRoundScreen(
                            questions = current.questions,
                            settings = settings,
                            onAnswer = { question, correct ->
                                scope.launch { store.recordQuestionAnswer(question, correct) }
                            },
                            onComplete = { engine ->
                                val code = current.invitation ?: FriendChallengeCode(
                                    seed = current.seed,
                                    targetScore = engine.score,
                                    targetPoints = engine.points,
                                )
                                val friendResult = FriendChallengeResult(
                                    code = code,
                                    score = engine.score,
                                    total = engine.questions.size,
                                    points = engine.points,
                                    outcomes = engine.outcomes,
                                    createdChallenge = current.invitation == null,
                                    dateMillis = System.currentTimeMillis(),
                                )
                                screen = FriendScreen.Result(friendResult)
                                scope.launch {
                                    store.recordFriendChallenge(
                                        result = friendResult,
                                        categories = engine.questions.mapTo(mutableSetOf()) { it.category },
                                    )
                                }
                            },
                            onExit = { screen = FriendScreen.Lobby() },
                        )
                        is FriendScreen.Result -> FriendChallengeResultScreen(
                            result = current.result,
                            onHome = onBackToPlay,
                        )
                    }
                },
                onFailure = { error ->
                    FriendCatalogErrorScreen(
                        message = error.message ?: "Unknown catalog error",
                        onBack = onBackToPlay,
                    )
                },
            )
        }
    }
}

@Composable
fun FriendChallengeHomeCard(onClick: () -> Unit) {
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
                modifier = Modifier.size(54.dp),
                shape = RoundedCornerShape(16.dp),
                color = friendChallengeTint.copy(alpha = 0.14f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("2", color = friendChallengeTint, fontWeight = FontWeight.Bold)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Friend Challenge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Play a random set, then share it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
fun FriendChallengeLobbyScreen(
    playerState: PlayerState,
    initialInput: String?,
    onBack: () -> Unit,
    onCreateChallenge: () -> Unit,
    onOpenChallenge: (FriendChallengeCode) -> Unit,
) {
    var codeText by remember(initialInput) { mutableStateOf(initialInput.orEmpty()) }
    var triedInvalidCode by remember(initialInput) { mutableStateOf(!initialInput.isNullOrBlank()) }
    val parsedCode = remember(codeText) { FriendChallengeLink.codeFrom(codeText) }
    val existingResult = parsedCode?.let(playerState::friendChallengeResult)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Categories") }
            Spacer(modifier = Modifier.weight(1f))
            Text("Friend Challenge", fontWeight = FontWeight.Bold)
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Same questions. One attempt.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Each challenge draws ten categories with the same Easy-to-Hard progression for both players.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCreateChallenge),
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    color = friendChallengeTint.copy(alpha = 0.14f),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Text("✦", color = friendChallengeTint, fontWeight = FontWeight.Bold)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Create a challenge", fontWeight = FontWeight.Bold)
                    Text(
                        "Finish the round to get a tap-to-open link and fallback code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("›", style = MaterialTheme.typography.headlineSmall)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Have a challenge link or code?", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = codeText,
                    onValueChange = {
                        codeText = it
                        triedInvalidCode = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("EZ3-XXXX-XXXX-XXXX-XXXX-XXX") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )

                if (parsedCode != null) {
                    Text(
                        "Target: ${parsedCode.targetScore}/10 · ${formatPoints(parsedCode.targetPoints)} points" +
                            if (existingResult == null) "" else " · already played",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = friendChallengeTint,
                    )
                } else if (triedInvalidCode && codeText.isNotBlank()) {
                    val message = when (FriendChallengeLink.rejectionReason(codeText)) {
                        is FriendChallengeCode.RejectionReason.UnsupportedVersion ->
                            "That code came from an older version of EZ Trivia. Ask your friend for a new one."
                        else -> "That challenge link or code is incomplete or has a typo."
                    }
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }

                Button(
                    onClick = {
                        val code = parsedCode
                        if (code == null) {
                            triedInvalidCode = true
                        } else {
                            codeText = code.displayString
                            onOpenChallenge(code)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = codeText.isNotBlank(),
                ) {
                    Text(if (existingResult == null) "Play this challenge" else "View your result")
                }
            }
        }
    }
}

@Composable
private fun FriendChallengeRoundScreen(
    questions: List<TriviaQuestion>,
    settings: AppSettings,
    onAnswer: (TriviaQuestion, Boolean) -> Unit,
    onComplete: (TriviaEngine) -> Unit,
    onExit: () -> Unit,
) {
    val engine = remember(questions) { TriviaEngine(questions) }
    val feedback = rememberAppFeedback()
    val view = LocalView.current
    var revision by remember(questions) { mutableIntStateOf(0) }
    var showExitConfirmation by remember { mutableStateOf(false) }

    @Suppress("UNUSED_EXPRESSION")
    revision

    BackHandler { showExitConfirmation = true }

    if (engine.isRoundComplete) {
        FriendLoadingScreen()
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
                Text("Friend Challenge", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text("${engine.score} correct", color = friendChallengeTint)
            }
        },
        bottomBar = {
            if (answered) {
                Surface(shadowElevation = 8.dp) {
                    Button(
                        onClick = ::advanceRound,
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                    ) {
                        val base = if (engine.currentIndex == engine.questions.lastIndex) "See results" else "Next question"
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
                        color = friendChallengeTint,
                    )
                }
            }

            question.visual?.let { visual ->
                item { FriendFlagVisual(visual, answered) }
            }

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
                FriendAnswerButton(
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

            if (answered) {
                item { FriendExplanationCard(question, engine.selectedAnswerIndex) }
            }
        }
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("Leave this challenge?") },
            text = {
                Text("Your attempt is saved only after all ten questions. You can enter the same code again if you leave now.")
            },
            confirmButton = { TextButton(onClick = onExit) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { showExitConfirmation = false }) { Text("Keep playing") } },
        )
    }
}

@Composable
private fun FriendAnswerButton(
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
        answered && isCorrect -> friendChallengeTint
        isSelectedWrong -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val container = when {
        answered && isCorrect -> friendChallengeTint.copy(alpha = 0.13f)
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
private fun FriendExplanationCard(question: TriviaQuestion, selectedIndex: Int?) {
    val correct = selectedIndex == question.correctAnswerIndex
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (correct) "Correct!" else "Good try!",
                style = MaterialTheme.typography.titleMedium,
                color = if (correct) friendChallengeTint else MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold,
            )
            Text(question.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FriendFlagVisual(visual: String, compact: Boolean) {
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
fun FriendChallengeResultScreen(
    result: FriendChallengeResult,
    onHome: () -> Unit,
) {
    val context = LocalContext.current
    var copied by remember(result.code.attemptId) { mutableStateOf(false) }
    val beatTarget = result.points > result.code.targetPoints
    val tiedTarget = result.points == result.code.targetPoints
    val resultTitle = when {
        result.createdChallenge -> "Challenge ready"
        beatTarget -> "You beat the challenge!"
        tiedTarget -> "It’s a tie!"
        else -> "Challenge complete"
    }
    val shareMessage = if (result.createdChallenge) {
        listOf(
            "I challenge you to EZ Trivia!",
            "Beat ${result.score}/${result.total} — ${formatPoints(result.points)} points — on the exact same questions.",
            "Tap to play: ${FriendChallengeLink.webUrl(result.code)}",
            "Challenge code: ${result.code.displayString}",
        ).joinToString("\n")
    } else {
        listOf(
            "EZ Trivia Friend Challenge — ${result.score}/${result.total}",
            RoundSummary.grid(result.outcomes),
            "${formatPoints(result.points)} points · target ${formatPoints(result.code.targetPoints)}",
            "Play this challenge: ${FriendChallengeLink.webUrl(result.code)}",
            "Challenge code: ${result.code.displayString}",
        ).joinToString("\n")
    }
    val shareHeadline = if (result.createdChallenge) {
        "Can you beat ${formatPoints(result.points)} points?"
    } else {
        "I scored ${result.score}/${result.total} on an EZ Trivia challenge"
    }
    val shareCard = ScoreCardContent(
        title = "Friend Challenge",
        subtitle = if (result.createdChallenge) "Can you beat me?" else "Challenge result",
        headline = "${result.score}/${result.total}",
        outcomes = result.outcomes,
        footnote = if (result.createdChallenge) {
            "${formatPoints(result.points)} point target"
        } else {
            "Target: ${formatPoints(result.code.targetPoints)} points"
        },
        tintArgb = friendChallengeTint.toArgb(),
    )

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(modifier = Modifier.size(92.dp), shape = CircleShape, color = friendChallengeTint.copy(alpha = 0.14f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(
                    if (result.createdChallenge) "↗" else if (beatTarget) "★" else "✓",
                    style = MaterialTheme.typography.headlineLarge,
                    color = friendChallengeTint,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Text(resultTitle, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            "${result.score} / ${result.total}",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = friendChallengeTint,
        )
        Text(RoundSummary.grid(result.outcomes), style = MaterialTheme.typography.headlineSmall, maxLines = 1)

        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            ResultStat(formatPoints(result.points), "your points")
            if (!result.createdChallenge) ResultStat(formatPoints(result.code.targetPoints), "target")
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (result.createdChallenge) "Share this challenge" else "Challenge code",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (result.createdChallenge) {
                    Text(
                        "The shared link opens this exact challenge. The code is a fallback.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(result.code.displayString, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1)
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("EZ Trivia challenge code", result.code.displayString))
                        copied = true
                    },
                ) {
                    Text(if (copied) "Copied" else "Copy code")
                }
            }
        }

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
            Text(if (result.createdChallenge) "Challenge a friend" else "Share result")
        }

        Button(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("Back to categories") }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ResultStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FriendLoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("Preparing challenge…")
        }
    }
}

@Composable
private fun FriendCatalogErrorScreen(message: String, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Challenge unavailable", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onBack) { Text("Back to categories") }
        }
    }
}

private fun formatPoints(points: Int): String = NumberFormat.getIntegerInstance().format(points)

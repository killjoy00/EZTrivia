package com.rsm.eztrivia.ui

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.rsm.eztrivia.data.AppSettings
import com.rsm.eztrivia.data.AppSettingsPolicy
import com.rsm.eztrivia.data.AppSettingsStore
import kotlinx.coroutines.delay

@Composable
fun rememberAppSettingsStore(): AppSettingsStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { AppSettingsStore(context) }
}

@Composable
fun rememberAppSettings(): AppSettings {
    val store = rememberAppSettingsStore()
    val settings by store.state.collectAsState()
    return settings
}

/**
 * Returns a visible countdown while auto-advance is armed.
 *
 * Touch exploration disables the timer automatically. A TalkBack user should
 * never have the explanation move out from under them merely because another
 * player prefers auto-advance.
 */
@Composable
fun rememberAutoAdvanceCountdown(
    questionIndex: Int,
    selectedAnswerIndex: Int?,
    settings: AppSettings,
    onElapsed: () -> Unit,
): Int? {
    val context = LocalContext.current
    val accessibilityManager = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    }
    val touchExplorationEnabled = accessibilityManager.isTouchExplorationEnabled
    var remaining by remember(
        questionIndex,
        selectedAnswerIndex,
        settings.autoAdvanceEnabled,
        settings.autoAdvanceSeconds,
        touchExplorationEnabled,
    ) { mutableStateOf<Int?>(null) }
    val latestOnElapsed by rememberUpdatedState(onElapsed)

    LaunchedEffect(
        questionIndex,
        selectedAnswerIndex,
        settings.autoAdvanceEnabled,
        settings.autoAdvanceSeconds,
        touchExplorationEnabled,
    ) {
        val answered = selectedAnswerIndex != null
        if (!AppSettingsPolicy.autoAdvanceShouldRun(settings, answered, touchExplorationEnabled)) {
            remaining = null
            return@LaunchedEffect
        }

        var seconds = settings.autoAdvanceSeconds
        remaining = seconds
        while (seconds > 0) {
            delay(1_000)
            seconds -= 1
            remaining = seconds.takeIf { it > 0 }
        }
        latestOnElapsed()
    }

    return remaining
}

fun roundActionLabel(base: String, remaining: Int?): String =
    if (remaining != null && remaining > 0) "$base · ${remaining}s" else base

fun Modifier.questionHeading(): Modifier = semantics { heading() }

fun Modifier.answerAccessibility(
    letter: Char,
    answer: String,
    answered: Boolean,
    isCorrect: Boolean,
    isSelectedWrong: Boolean,
): Modifier = semantics(mergeDescendants = true) {
    contentDescription = "Answer $letter: $answer"
    if (answered) {
        stateDescription = when {
            isCorrect -> "Correct answer"
            isSelectedWrong -> "Your answer, incorrect"
            else -> "Not selected"
        }
    }
}

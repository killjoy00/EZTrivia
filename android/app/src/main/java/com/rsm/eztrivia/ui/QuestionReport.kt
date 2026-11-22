package com.rsm.eztrivia.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.rsm.eztrivia.BuildConfig
import com.rsm.eztrivia.model.TriviaQuestion

internal data class QuestionReportDraft(val subject: String, val body: String)

/**
 * Same prefilled question context as iOS's QuestionReport, without any
 * automatic sending. The player can inspect/edit the email before sending.
 */
internal fun questionReportDraft(
    question: TriviaQuestion,
    selectedAnswerIndex: Int?,
    version: String,
): QuestionReportDraft {
    val selectedAnswer = selectedAnswerIndex?.let { question.answers.getOrNull(it) } ?: "Not recorded"
    return QuestionReportDraft(
        subject = "EZ Trivia question report — ${question.id}",
        body = buildString {
            appendLine("Please describe what seems wrong with this question:")
            appendLine()
            appendLine()
            appendLine("--- Question details ---")
            appendLine("ID: ${question.id}")
            appendLine("Category: ${question.category.title}")
            appendLine("Difficulty: ${question.difficulty.title}")
            appendLine("Prompt: ${question.prompt}")
            appendLine("Selected answer: $selectedAnswer")
            appendLine("Correct answer: ${question.correctAnswer}")
            appendLine("Explanation: ${question.explanation}")
            appendLine("App version: $version")
        },
    )
}

@Composable
internal fun QuestionReportButton(question: TriviaQuestion, selectedAnswerIndex: Int?) {
    val context = LocalContext.current
    HorizontalDivider()
    TextButton(onClick = { openQuestionReport(context, question, selectedAnswerIndex) }) {
        Text("Report this question")
    }
}

private fun openQuestionReport(
    context: Context,
    question: TriviaQuestion,
    selectedAnswerIndex: Int?,
) {
    val draft = questionReportDraft(
        question = question,
        selectedAnswerIndex = selectedAnswerIndex,
        version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
    )
    val uri = Uri.parse("mailto:killjoy00@yahoo.com")
        .buildUpon()
        .appendQueryParameter("subject", draft.subject)
        .appendQueryParameter("body", draft.body)
        .build()
    val intent = Intent(Intent.ACTION_SENDTO, uri)
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        // Same fallback as Settings support when no email app is installed.
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://killjoy00.github.io/EZTrivia/support.html"))
        )
    }
}

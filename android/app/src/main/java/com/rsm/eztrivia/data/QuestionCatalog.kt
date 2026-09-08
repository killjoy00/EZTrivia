package com.rsm.eztrivia.data

import android.content.Context
import com.rsm.eztrivia.model.TriviaCategory
import com.rsm.eztrivia.model.TriviaDifficulty
import com.rsm.eztrivia.model.TriviaQuestion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class CatalogPayload(
    val schemaVersion: Int,
    val questions: List<QuestionPayload>,
)

@Serializable
private data class QuestionPayload(
    val id: String,
    val category: String,
    val difficulty: String,
    val prompt: String,
    val visual: String? = null,
    val answers: List<String>,
    val correctAnswerIndex: Int,
    val explanation: String,
    val flagCode: String? = null,
    val confusableFlagCodes: Set<String> = emptySet(),
)

object QuestionCatalog {
    private val json = Json { ignoreUnknownKeys = false }

    fun load(context: Context): List<TriviaQuestion> =
        context.assets.open("questions.json").bufferedReader().use { reader ->
            decode(reader.readText())
        }

    fun decode(rawJson: String): List<TriviaQuestion> {
        val payload = json.decodeFromString<CatalogPayload>(rawJson)
        require(payload.schemaVersion == 1) { "Unsupported question catalog schema: ${payload.schemaVersion}" }
        return payload.questions.map { question ->
            TriviaQuestion(
                id = question.id,
                category = TriviaCategory.fromWireName(question.category),
                prompt = question.prompt,
                difficulty = TriviaDifficulty.fromWireName(question.difficulty),
                visual = question.visual,
                answers = question.answers,
                correctAnswerIndex = question.correctAnswerIndex,
                explanation = question.explanation,
                flagCode = question.flagCode,
                confusableFlagCodes = question.confusableFlagCodes,
            )
        }
    }
}

package com.rsm.eztrivia.data

import android.content.Context
import com.rsm.eztrivia.model.TriviaCategory
import com.rsm.eztrivia.model.TriviaDifficulty
import com.rsm.eztrivia.model.TriviaQuestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

    // 2341 questions is roughly a megabyte of JSON. Reading and decoding it is
    // far too much work for a frame, and `produceState` runs its block on the
    // composition's dispatcher -- the main thread -- so the previous blocking
    // call froze the UI behind its own loading spinner.
    //
    // The result is also held for the life of the process. Three entry points
    // load the catalog (the main app, the Daily activity, and the Friend
    // Challenge activity) and each used to parse its own copy.
    @Volatile
    private var cached: List<TriviaQuestion>? = null
    private val loadMutex = Mutex()

    suspend fun load(context: Context): List<TriviaQuestion> {
        cached?.let { return it }
        val appContext = context.applicationContext
        return loadMutex.withLock {
            // Re-checked inside the lock: concurrent callers that all missed the
            // fast path above would otherwise each decode the catalog.
            cached ?: withContext(Dispatchers.IO) {
                appContext.assets.open("questions.json").bufferedReader().use { reader ->
                    decode(reader.readText())
                }
            }.also { cached = it }
        }
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

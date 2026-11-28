package com.rsm.eztrivia.data

import android.content.Context
import com.rsm.eztrivia.model.FriendChallenge
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
    val friendV4Questions: List<QuestionPayload>,
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
    class Bank internal constructor(
        val questions: List<TriviaQuestion>,
        private val friendV4Questions: List<TriviaQuestion>,
    ) {
        fun forFriendChallenge(version: Int): List<TriviaQuestion> =
            if (version == FriendChallenge.FIRST_EXPANDED_CODE_VERSION) friendV4Questions else questions
    }

    private val json = Json { ignoreUnknownKeys = false }

    // Reading and decoding the bundled catalog is too much work for a frame.
    // The `produceState` composable runs its block on the
    // composition's dispatcher -- the main thread -- so the previous blocking
    // call froze the UI behind its own loading spinner.
    //
    // The result is also held for the life of the process. Three entry points
    // load the catalog (the main app, the Daily activity, and the Friend
    // Challenge activity) and each used to parse its own copy.
    @Volatile
    private var cached: Bank? = null
    private val loadMutex = Mutex()

    suspend fun load(context: Context): List<TriviaQuestion> = loadBank(context).questions

    suspend fun loadBank(context: Context): Bank {
        cached?.let { return it }
        val appContext = context.applicationContext
        return loadMutex.withLock {
            // Re-checked inside the lock: concurrent callers that all missed the
            // fast path above would otherwise each decode the catalog.
            cached ?: withContext(Dispatchers.IO) {
                appContext.assets.open("questions.json").bufferedReader().use { reader ->
                    decodeBank(reader.readText())
                }
            }.also { cached = it }
        }
    }

    fun decode(rawJson: String): List<TriviaQuestion> = decodeBank(rawJson).questions

    fun decodeBank(rawJson: String): Bank {
        val payload = json.decodeFromString<CatalogPayload>(rawJson)
        require(payload.schemaVersion == 1) { "Unsupported question catalog schema: ${payload.schemaVersion}" }
        val current = payload.questions.map { it.toQuestion() }
        val originals = payload.friendV4Questions.associate { it.id to it.toQuestion() }
        val version4 = current.map { question ->
            val legacyId = question.id.replace("-v5-", "-")
            if (legacyId == question.id) question else originals.getValue(legacyId)
        }
        return Bank(current, version4)
    }

    private fun QuestionPayload.toQuestion() = TriviaQuestion(
        id = id,
        category = TriviaCategory.fromWireName(category),
        prompt = prompt,
        difficulty = TriviaDifficulty.fromWireName(difficulty),
        visual = visual,
        answers = answers,
        correctAnswerIndex = correctAnswerIndex,
        explanation = explanation,
        flagCode = flagCode,
        confusableFlagCodes = confusableFlagCodes,
    )
}

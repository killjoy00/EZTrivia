package com.rsm.eztrivia.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rsm.eztrivia.model.FriendChallengeCode
import com.rsm.eztrivia.model.TriviaCategory
import com.rsm.eztrivia.model.TriviaDifficulty
import com.rsm.eztrivia.model.TriviaQuestion
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.playerStateDataStore by preferencesDataStore(name = "eztrivia_player_state")

@Serializable
data class CategoryRoundResult(
    val id: String,
    val category: String,
    val difficulty: String,
    val score: Int,
    val total: Int,
    val dateMillis: Long,
) {
    val percentage: Int
        get() = if (total == 0) 0 else ((score.toDouble() / total.toDouble()) * 100.0).toInt()
}

@Serializable
data class QuickPlayResult(
    val id: String,
    val score: Int,
    val total: Int,
    val points: Int,
    val outcomes: List<Boolean>,
    val dateMillis: Long,
)

@Serializable
data class FriendChallengeResult(
    val code: FriendChallengeCode,
    val score: Int,
    val total: Int,
    val points: Int,
    val outcomes: List<Boolean>,
    val createdChallenge: Boolean,
    val dateMillis: Long,
)

@Serializable
data class PlayerState(
    val schemaVersion: Int = 1,
    val recentCategoryResults: List<CategoryRoundResult> = emptyList(),
    val quickPlayResults: List<QuickPlayResult> = emptyList(),
    val friendChallengeResultsByAttemptId: Map<String, FriendChallengeResult> = emptyMap(),
    val seenQuestionIds: Map<String, Set<String>> = emptyMap(),
    val completedQuestionIds: Set<String> = emptySet(),
    val correctlyAnsweredQuestionIds: Set<String> = emptySet(),
    val lifetimePointsByCategory: Map<String, Int> = emptyMap(),
    val totalRoundsCompleted: Int = 0,
    val quickPlayRoundsCompleted: Int = 0,
    val playedCategoryRawValues: Set<String> = emptySet(),
    val perfectDifficultyRawValues: Set<String> = emptySet(),
) {
    val allSeenQuestionIds: Set<String>
        get() = seenQuestionIds.values.fold(emptySet()) { accumulated, ids -> accumulated + ids }

    val lifetimePointsTotal: Int
        get() = lifetimePointsByCategory.values.sum()

    val friendChallengesCompleted: Int
        get() = friendChallengeResultsByAttemptId.size

    fun seenQuestions(category: TriviaCategory, difficulty: TriviaDifficulty): Set<String> =
        seenQuestionIds[PlayerStateReducer.cacheKey(category, difficulty)].orEmpty()

    fun friendChallengeResult(code: FriendChallengeCode): FriendChallengeResult? =
        friendChallengeResultsByAttemptId[code.attemptId]
}

object PlayerStateReducer {
    fun cacheKey(category: TriviaCategory, difficulty: TriviaDifficulty): String =
        "${category.wireName}-${difficulty.wireName}"

    fun markSeen(
        state: PlayerState,
        ids: Set<String>,
        category: TriviaCategory,
        difficulty: TriviaDifficulty,
        availableCount: Int,
    ): PlayerState {
        if (ids.isEmpty()) return state
        val key = cacheKey(category, difficulty)
        val merged = state.seenQuestionIds[key].orEmpty() + ids
        val nextIds = if (availableCount > 0 && merged.size >= availableCount) ids else merged
        return state.copy(seenQuestionIds = state.seenQuestionIds + (key to nextIds))
    }

    fun recordQuestionAnswer(
        state: PlayerState,
        questionId: String,
        correct: Boolean,
    ): PlayerState {
        val completed = state.completedQuestionIds + questionId
        val correctlyAnswered = if (correct) {
            state.correctlyAnsweredQuestionIds + questionId
        } else {
            state.correctlyAnsweredQuestionIds
        }
        if (completed == state.completedQuestionIds && correctlyAnswered == state.correctlyAnsweredQuestionIds) {
            return state
        }
        return state.copy(
            completedQuestionIds = completed,
            correctlyAnsweredQuestionIds = correctlyAnswered,
        )
    }

    fun recordCategoryRound(
        state: PlayerState,
        category: TriviaCategory,
        difficulty: TriviaDifficulty,
        score: Int,
        total: Int,
        points: Int,
        id: String = UUID.randomUUID().toString(),
        dateMillis: Long = System.currentTimeMillis(),
    ): PlayerState {
        val result = CategoryRoundResult(
            id = id,
            category = category.wireName,
            difficulty = difficulty.wireName,
            score = score,
            total = total,
            dateMillis = dateMillis,
        )
        val recent = (state.recentCategoryResults + result)
            .sortedByDescending(CategoryRoundResult::dateMillis)
            .take(50)
        val lifetime = state.lifetimePointsByCategory.toMutableMap().apply {
            this[category.wireName] = getOrDefault(category.wireName, 0) + points
        }
        val perfect = if (score == total && total > 0) {
            state.perfectDifficultyRawValues + difficulty.wireName
        } else {
            state.perfectDifficultyRawValues
        }

        return state.copy(
            recentCategoryResults = recent,
            lifetimePointsByCategory = lifetime,
            totalRoundsCompleted = state.totalRoundsCompleted + 1,
            playedCategoryRawValues = state.playedCategoryRawValues + category.wireName,
            perfectDifficultyRawValues = perfect,
        )
    }

    fun recordQuickPlay(
        state: PlayerState,
        score: Int,
        total: Int,
        points: Int,
        outcomes: List<Boolean>,
        categories: Set<TriviaCategory>,
        id: String = UUID.randomUUID().toString(),
        dateMillis: Long = System.currentTimeMillis(),
    ): PlayerState {
        val result = QuickPlayResult(
            id = id,
            score = score,
            total = total,
            points = points,
            outcomes = outcomes,
            dateMillis = dateMillis,
        )
        val recent = (state.quickPlayResults + result)
            .sortedByDescending(QuickPlayResult::dateMillis)
            .take(20)

        return state.copy(
            quickPlayResults = recent,
            totalRoundsCompleted = state.totalRoundsCompleted + 1,
            quickPlayRoundsCompleted = state.quickPlayRoundsCompleted + 1,
            playedCategoryRawValues = state.playedCategoryRawValues + categories.map(TriviaCategory::wireName),
        )
    }

    fun recordFriendChallenge(
        state: PlayerState,
        result: FriendChallengeResult,
        categories: Set<TriviaCategory>,
    ): PlayerState {
        val attemptId = result.code.attemptId
        if (attemptId in state.friendChallengeResultsByAttemptId) return state

        return state.copy(
            friendChallengeResultsByAttemptId = state.friendChallengeResultsByAttemptId + (attemptId to result),
            totalRoundsCompleted = state.totalRoundsCompleted + 1,
            playedCategoryRawValues = state.playedCategoryRawValues + categories.map(TriviaCategory::wireName),
        )
    }

    /** Mirrors iOS ScoreStore.clear(): clear recent category history and seen-cycle state only. */
    fun clearRecentCategoryHistory(state: PlayerState): PlayerState =
        state.copy(
            recentCategoryResults = emptyList(),
            seenQuestionIds = emptyMap(),
        )
}

class PlayerStateStore(context: Context) {
    private val dataStore = context.applicationContext.playerStateDataStore
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateKey = stringPreferencesKey("player_state_v1")

    val state: StateFlow<PlayerState> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> decode(preferences[stateKey]) }
        .stateIn(scope, SharingStarted.Eagerly, PlayerState())

    val current: PlayerState
        get() = state.value

    suspend fun markSeen(
        ids: Set<String>,
        category: TriviaCategory,
        difficulty: TriviaDifficulty,
        availableCount: Int,
    ) {
        update { current ->
            PlayerStateReducer.markSeen(current, ids, category, difficulty, availableCount)
        }
    }

    suspend fun recordQuestionAnswer(question: TriviaQuestion, correct: Boolean) {
        update { current -> PlayerStateReducer.recordQuestionAnswer(current, question.id, correct) }
    }

    suspend fun recordCategoryRound(
        category: TriviaCategory,
        difficulty: TriviaDifficulty,
        score: Int,
        total: Int,
        points: Int,
    ) {
        update { current ->
            PlayerStateReducer.recordCategoryRound(
                state = current,
                category = category,
                difficulty = difficulty,
                score = score,
                total = total,
                points = points,
            )
        }
    }

    suspend fun recordQuickPlay(
        score: Int,
        total: Int,
        points: Int,
        outcomes: List<Boolean>,
        categories: Set<TriviaCategory>,
    ) {
        update { current ->
            PlayerStateReducer.recordQuickPlay(
                state = current,
                score = score,
                total = total,
                points = points,
                outcomes = outcomes,
                categories = categories,
            )
        }
    }

    suspend fun recordFriendChallenge(
        result: FriendChallengeResult,
        categories: Set<TriviaCategory>,
    ) {
        update { current -> PlayerStateReducer.recordFriendChallenge(current, result, categories) }
    }

    suspend fun clearRecentCategoryHistory() {
        update(PlayerStateReducer::clearRecentCategoryHistory)
    }

    private suspend fun update(transform: (PlayerState) -> PlayerState) {
        dataStore.edit { preferences ->
            val current = decode(preferences[stateKey])
            val updated = transform(current)
            if (updated != current) {
                preferences[stateKey] = json.encodeToString(updated)
            }
        }
    }

    private fun decode(raw: String?): PlayerState {
        if (raw.isNullOrBlank()) return PlayerState()
        return runCatching { json.decodeFromString<PlayerState>(raw) }
            .getOrElse { PlayerState() }
            .takeIf { it.schemaVersion == 1 }
            ?: PlayerState()
    }
}

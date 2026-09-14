package com.rsm.eztrivia.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rsm.eztrivia.model.DailyStreak
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
import kotlinx.coroutines.flow.first
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
data class DailyResult(
    val day: Int,
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
    val dailyResultsByDay: Map<Int, DailyResult> = emptyMap(),
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

    fun dailyResult(day: Int): DailyResult? = dailyResultsByDay[day]

    fun dailyStreak(today: Int): Int = DailyStreak.current(dailyResultsByDay.keys, today)

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

    fun recordDaily(
        state: PlayerState,
        result: DailyResult,
        categories: Set<TriviaCategory>,
    ): PlayerState {
        if (result.day in state.dailyResultsByDay) return state
        return state.copy(
            dailyResultsByDay = state.dailyResultsByDay + (result.day to result),
            totalRoundsCompleted = state.totalRoundsCompleted + 1,
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
    private val appContext = context.applicationContext
    private val dataStore = appContext.playerStateDataStore
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateKey = stringPreferencesKey("player_state_v1")
    private val syncKey = stringPreferencesKey("player_sync_v1")

    // Deliberately stored outside DataStore. The app's backup rules whitelist
    // only the two DataStore files, so a restored/new device gets a new ID while
    // the backed-up per-device contribution map remains intact.
    private val installationId: String = appContext
        .getSharedPreferences("eztrivia_installation", Context.MODE_PRIVATE)
        .let { preferences ->
            preferences.getString("installation_id", null)?.takeIf(String::isNotBlank)
                ?: UUID.randomUUID().toString().also { generated ->
                    preferences.edit().putString("installation_id", generated).apply()
                }
        }

    val state: StateFlow<PlayerState> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            decodeEnvelope(preferences[stateKey], preferences[syncKey]).playerState
        }
        .stateIn(scope, SharingStarted.Eagerly, PlayerState())

    val current: PlayerState
        get() = state.value

    /** The persisted state, awaited rather than sampled. See `AppSettingsStore.loaded`. */
    suspend fun loaded(): PlayerState = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> decodeEnvelope(preferences[stateKey], preferences[syncKey]).playerState }
        .first()

    suspend fun persistedDailyResult(day: Int): DailyResult? {
        val preferences = dataStore.data
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }
            .first()
        return decodeEnvelope(preferences[stateKey], preferences[syncKey]).playerState.dailyResult(day)
    }

    /** Reads disk-backed state before deciding whether an external challenge is replayable. */
    suspend fun persistedFriendChallengeResult(code: FriendChallengeCode): FriendChallengeResult? {
        val preferences = dataStore.data
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }
            .first()
        return decodeEnvelope(preferences[stateKey], preferences[syncKey])
            .playerState
            .friendChallengeResult(code)
    }

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
        updateWithSync { current, sync ->
            val nextPlayer = PlayerStateReducer.recordCategoryRound(
                state = current,
                category = category,
                difficulty = difficulty,
                score = score,
                total = total,
                points = points,
            )
            val withPoints = PlayerStateSync.addLifetimePoints(
                syncState = sync,
                installationId = installationId,
                category = category.wireName,
                points = points,
            )
            nextPlayer to PlayerStateSync.incrementCompletedRound(
                syncState = withPoints,
                installationId = installationId,
                quickPlay = false,
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
        updateWithSync { current, sync ->
            val nextPlayer = PlayerStateReducer.recordQuickPlay(
                state = current,
                score = score,
                total = total,
                points = points,
                outcomes = outcomes,
                categories = categories,
            )
            nextPlayer to PlayerStateSync.incrementCompletedRound(
                syncState = sync,
                installationId = installationId,
                quickPlay = true,
            )
        }
    }

    suspend fun recordDaily(
        result: DailyResult,
        categories: Set<TriviaCategory>,
    ) {
        updateWithSync { current, sync ->
            val nextPlayer = PlayerStateReducer.recordDaily(current, result, categories)
            if (nextPlayer == current) {
                current to sync
            } else {
                nextPlayer to PlayerStateSync.incrementCompletedRound(
                    syncState = sync,
                    installationId = installationId,
                    quickPlay = false,
                )
            }
        }
    }

    suspend fun recordFriendChallenge(
        result: FriendChallengeResult,
        categories: Set<TriviaCategory>,
    ) {
        updateWithSync { current, sync ->
            val nextPlayer = PlayerStateReducer.recordFriendChallenge(current, result, categories)
            if (nextPlayer == current) {
                current to sync
            } else {
                nextPlayer to PlayerStateSync.incrementCompletedRound(
                    syncState = sync,
                    installationId = installationId,
                    quickPlay = false,
                )
            }
        }
    }

    suspend fun clearRecentCategoryHistory() {
        val resetAtMillis = System.currentTimeMillis()
        updateWithSync { current, sync ->
            PlayerStateReducer.clearRecentCategoryHistory(current) to
                PlayerStateSync.noteHistoryReset(sync, resetAtMillis)
        }
    }

    /**
     * Returns a disk-backed, normalized snapshot suitable for Saved Games and
     * persists any one-time migration of legacy scalar totals atomically.
     */
    suspend fun cloudEnvelope(): CloudPlayerState {
        var result = CloudPlayerState(playerState = PlayerState(), syncState = PlayerSyncState())
        dataStore.edit { preferences ->
            val rawPlayer = decodePlayer(preferences[stateKey])
            val rawSync = decodeSync(preferences[syncKey])
            result = PlayerStateSync.prepareEnvelope(rawPlayer, rawSync)
            persistIfChanged(preferences, rawPlayer, rawSync, result)
        }
        return result
    }

    /** Merges remote Saved Games data into local storage without losing either side's progress. */
    suspend fun mergeCloudEnvelope(remote: CloudPlayerState): CloudPlayerState {
        var result = CloudPlayerState(playerState = PlayerState(), syncState = PlayerSyncState())
        dataStore.edit { preferences ->
            val rawPlayer = decodePlayer(preferences[stateKey])
            val rawSync = decodeSync(preferences[syncKey])
            val local = PlayerStateSync.prepareEnvelope(rawPlayer, rawSync)
            result = PlayerStateSync.merge(local, remote)
            persistIfChanged(preferences, rawPlayer, rawSync, result)
        }
        return result
    }

    fun encodeCloudEnvelope(envelope: CloudPlayerState): ByteArray =
        json.encodeToString(envelope).toByteArray(Charsets.UTF_8)

    /** Empty bytes represent a newly created cloud slot; non-empty invalid data is rejected. */
    fun decodeCloudEnvelope(bytes: ByteArray): CloudPlayerState? {
        if (bytes.isEmpty()) {
            return PlayerStateSync.prepareEnvelope(PlayerState(), PlayerSyncState())
        }
        val decoded = runCatching {
            json.decodeFromString<CloudPlayerState>(bytes.toString(Charsets.UTF_8))
        }.getOrNull() ?: return null
        if (
            decoded.schemaVersion != 1 ||
            decoded.playerState.schemaVersion != 1 ||
            decoded.syncState.schemaVersion != 1
        ) {
            return null
        }
        return PlayerStateSync.prepareEnvelope(decoded.playerState, decoded.syncState)
    }

    private suspend fun update(transform: (PlayerState) -> PlayerState) {
        dataStore.edit { preferences ->
            val rawPlayer = decodePlayer(preferences[stateKey])
            val rawSync = decodeSync(preferences[syncKey])
            val current = PlayerStateSync.prepareEnvelope(rawPlayer, rawSync)
            val updated = PlayerStateSync.prepareEnvelope(
                playerState = transform(current.playerState),
                syncState = current.syncState,
            )
            persistIfChanged(preferences, rawPlayer, rawSync, updated)
        }
    }

    private suspend fun updateWithSync(
        transform: (PlayerState, PlayerSyncState) -> Pair<PlayerState, PlayerSyncState>,
    ) {
        dataStore.edit { preferences ->
            val rawPlayer = decodePlayer(preferences[stateKey])
            val rawSync = decodeSync(preferences[syncKey])
            val current = PlayerStateSync.prepareEnvelope(rawPlayer, rawSync)
            val (nextPlayer, nextSync) = transform(current.playerState, current.syncState)
            val updated = PlayerStateSync.prepareEnvelope(nextPlayer, nextSync)
            persistIfChanged(preferences, rawPlayer, rawSync, updated)
        }
    }

    private fun persistIfChanged(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        rawPlayer: PlayerState,
        rawSync: PlayerSyncState,
        updated: CloudPlayerState,
    ) {
        if (updated.playerState != rawPlayer) {
            preferences[stateKey] = json.encodeToString(updated.playerState)
        }
        if (updated.syncState != rawSync) {
            preferences[syncKey] = json.encodeToString(updated.syncState)
        }
    }

    private fun decodeEnvelope(playerRaw: String?, syncRaw: String?): CloudPlayerState =
        PlayerStateSync.prepareEnvelope(decodePlayer(playerRaw), decodeSync(syncRaw))

    private fun decodePlayer(raw: String?): PlayerState {
        if (raw.isNullOrBlank()) return PlayerState()
        return runCatching { json.decodeFromString<PlayerState>(raw) }
            .getOrElse { PlayerState() }
            .takeIf { it.schemaVersion == 1 }
            ?: PlayerState()
    }

    private fun decodeSync(raw: String?): PlayerSyncState {
        if (raw.isNullOrBlank()) return PlayerSyncState()
        return runCatching { json.decodeFromString<PlayerSyncState>(raw) }
            .getOrElse { PlayerSyncState() }
            .takeIf { it.schemaVersion == 1 }
            ?: PlayerSyncState()
    }
}

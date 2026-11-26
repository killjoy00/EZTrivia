package com.rsm.eztrivia.data

import kotlinx.serialization.Serializable

/**
 * Additive merge components for Google Play Games Saved Games.
 *
 * PlayerState intentionally keeps its original on-device JSON shape so older
 * Android builds can still read the main gameplay state. These components live
 * under a second DataStore key and travel with the cloud envelope. Existing
 * scalar totals become a shared baseline the first time cloud sync is enabled;
 * later play is counted per installation and merged with max-per-device rules,
 * so two devices can both make offline progress without either clobbering the
 * other or double-counting the same device's upload.
 */
@Serializable
data class PlayerSyncState(
    val schemaVersion: Int = 1,
    val initialized: Boolean = false,
    val lifetimeBaseByCategory: Map<String, Int> = emptyMap(),
    val lifetimeIncrementsByDevice: Map<String, Map<String, Int>> = emptyMap(),
    val totalRoundsBaseline: Int = 0,
    val totalRoundIncrementsByDevice: Map<String, Int> = emptyMap(),
    val quickPlayRoundsBaseline: Int = 0,
    val quickPlayRoundIncrementsByDevice: Map<String, Int> = emptyMap(),
    val recentHistoryResetAtMillis: Long = 0L,
    val seenQuestionsResetAtMillis: Long = 0L,
)

@Serializable
data class CloudPlayerState(
    val schemaVersion: Int = 1,
    val playerState: PlayerState,
    val syncState: PlayerSyncState,
)

object PlayerStateSync {
    fun prepareEnvelope(
        playerState: PlayerState,
        syncState: PlayerSyncState,
    ): CloudPlayerState {
        val normalizedSync = normalizeSync(playerState, syncState)
        return CloudPlayerState(
            playerState = canonicalize(playerState, normalizedSync),
            syncState = normalizedSync,
        )
    }

    /**
     * Converts pre-cloud scalar totals into one shared baseline. If an older app
     * later changed only the scalar PlayerState fields, fold any positive delta
     * back into the baseline so an upgrade does not silently lose that play.
     */
    fun normalizeSync(
        playerState: PlayerState,
        syncState: PlayerSyncState,
    ): PlayerSyncState {
        if (!syncState.initialized || syncState.schemaVersion != 1) {
            return PlayerSyncState(
                initialized = true,
                lifetimeBaseByCategory = playerState.lifetimePointsByCategory,
                totalRoundsBaseline = playerState.totalRoundsCompleted,
                quickPlayRoundsBaseline = playerState.quickPlayRoundsCompleted,
                recentHistoryResetAtMillis = syncState.recentHistoryResetAtMillis,
                seenQuestionsResetAtMillis = syncState.seenQuestionsResetAtMillis,
            )
        }

        val lifetimeBase = syncState.lifetimeBaseByCategory.toMutableMap()
        val categories = playerState.lifetimePointsByCategory.keys +
            syncState.lifetimeBaseByCategory.keys +
            syncState.lifetimeIncrementsByDevice.values.flatMap { it.keys }
        for (category in categories) {
            val devicePoints = syncState.lifetimeIncrementsByDevice.values.sumOfSafely {
                it[category] ?: 0
            }
            val scalarPoints = playerState.lifetimePointsByCategory[category] ?: 0
            val requiredBase = (scalarPoints.toLong() - devicePoints.toLong())
                .coerceAtLeast(0L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
            lifetimeBase[category] = maxOf(lifetimeBase[category] ?: 0, requiredBase)
        }

        val totalDeviceRounds = syncState.totalRoundIncrementsByDevice.values.sumSafely()
        val requiredTotalBase = (playerState.totalRoundsCompleted.toLong() - totalDeviceRounds.toLong())
            .coerceAtLeast(0L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        val quickDeviceRounds = syncState.quickPlayRoundIncrementsByDevice.values.sumSafely()
        val requiredQuickBase = (playerState.quickPlayRoundsCompleted.toLong() - quickDeviceRounds.toLong())
            .coerceAtLeast(0L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

        return syncState.copy(
            initialized = true,
            lifetimeBaseByCategory = lifetimeBase,
            totalRoundsBaseline = maxOf(syncState.totalRoundsBaseline, requiredTotalBase),
            quickPlayRoundsBaseline = maxOf(syncState.quickPlayRoundsBaseline, requiredQuickBase),
        )
    }

    fun addLifetimePoints(
        syncState: PlayerSyncState,
        installationId: String,
        category: String,
        points: Int,
    ): PlayerSyncState {
        if (points <= 0) return syncState
        val deviceTotals = syncState.lifetimeIncrementsByDevice[installationId]
            .orEmpty()
            .toMutableMap()
        deviceTotals[category] = safeAdd(deviceTotals[category] ?: 0, points)
        return syncState.copy(
            lifetimeIncrementsByDevice = syncState.lifetimeIncrementsByDevice +
                (installationId to deviceTotals),
        )
    }

    fun incrementCompletedRound(
        syncState: PlayerSyncState,
        installationId: String,
        quickPlay: Boolean,
    ): PlayerSyncState {
        val totalByDevice = syncState.totalRoundIncrementsByDevice.toMutableMap()
        totalByDevice[installationId] = safeAdd(totalByDevice[installationId] ?: 0, 1)

        val quickByDevice = if (quickPlay) {
            syncState.quickPlayRoundIncrementsByDevice.toMutableMap().apply {
                this[installationId] = safeAdd(this[installationId] ?: 0, 1)
            }
        } else {
            syncState.quickPlayRoundIncrementsByDevice
        }

        return syncState.copy(
            totalRoundIncrementsByDevice = totalByDevice,
            quickPlayRoundIncrementsByDevice = quickByDevice,
        )
    }

    fun noteHistoryReset(
        syncState: PlayerSyncState,
        resetAtMillis: Long,
    ): PlayerSyncState = syncState.copy(
        recentHistoryResetAtMillis = maxOf(syncState.recentHistoryResetAtMillis, resetAtMillis),
        seenQuestionsResetAtMillis = maxOf(syncState.seenQuestionsResetAtMillis, resetAtMillis),
    )

    fun merge(
        localRaw: CloudPlayerState,
        remoteRaw: CloudPlayerState,
    ): CloudPlayerState {
        val local = prepareEnvelope(localRaw.playerState, localRaw.syncState)
        val remote = prepareEnvelope(remoteRaw.playerState, remoteRaw.syncState)

        val recentReset = maxOf(
            local.syncState.recentHistoryResetAtMillis,
            remote.syncState.recentHistoryResetAtMillis,
        )
        val seenReset = maxOf(
            local.syncState.seenQuestionsResetAtMillis,
            remote.syncState.seenQuestionsResetAtMillis,
        )

        val recentById = mutableMapOf<String, CategoryRoundResult>()
        (local.playerState.recentCategoryResults + remote.playerState.recentCategoryResults)
            .forEach { candidate ->
                val existing = recentById[candidate.id]
                recentById[candidate.id] = if (existing == null) {
                    candidate
                } else {
                    earliestCategoryResult(existing, candidate)
                }
            }
        val recent = recentById.values
            .filter { it.dateMillis > recentReset }
            .sortedWith(compareByDescending<CategoryRoundResult> { it.dateMillis }.thenBy { it.id })
            .take(50)

        val seen = when {
            local.syncState.seenQuestionsResetAtMillis > remote.syncState.seenQuestionsResetAtMillis ->
                local.playerState.seenQuestionIds
            remote.syncState.seenQuestionsResetAtMillis > local.syncState.seenQuestionsResetAtMillis ->
                remote.playerState.seenQuestionIds
            else -> mergeSeenQuestionIds(
                local.playerState.seenQuestionIds,
                remote.playerState.seenQuestionIds,
            )
        }

        val daily = mergeDailyResults(
            local.playerState.dailyResultsByDay,
            remote.playerState.dailyResultsByDay,
        )
        val friends = mergeFriendResults(
            local.playerState.friendChallengeResultsByAttemptId,
            remote.playerState.friendChallengeResultsByAttemptId,
        )
        val quickPlay = mergeQuickPlayResults(
            local.playerState.quickPlayResults,
            remote.playerState.quickPlayResults,
        )

        val mergedSync = PlayerSyncState(
            initialized = true,
            lifetimeBaseByCategory = mergeMaxMaps(
                local.syncState.lifetimeBaseByCategory,
                remote.syncState.lifetimeBaseByCategory,
            ),
            lifetimeIncrementsByDevice = mergeNestedMaxMaps(
                local.syncState.lifetimeIncrementsByDevice,
                remote.syncState.lifetimeIncrementsByDevice,
            ),
            totalRoundsBaseline = maxOf(
                local.syncState.totalRoundsBaseline,
                remote.syncState.totalRoundsBaseline,
            ),
            totalRoundIncrementsByDevice = mergeMaxMaps(
                local.syncState.totalRoundIncrementsByDevice,
                remote.syncState.totalRoundIncrementsByDevice,
            ),
            quickPlayRoundsBaseline = maxOf(
                local.syncState.quickPlayRoundsBaseline,
                remote.syncState.quickPlayRoundsBaseline,
            ),
            quickPlayRoundIncrementsByDevice = mergeMaxMaps(
                local.syncState.quickPlayRoundIncrementsByDevice,
                remote.syncState.quickPlayRoundIncrementsByDevice,
            ),
            recentHistoryResetAtMillis = recentReset,
            seenQuestionsResetAtMillis = seenReset,
        )

        val mergedPlayer = local.playerState.copy(
            recentCategoryResults = recent,
            quickPlayResults = quickPlay,
            dailyResultsByDay = daily,
            friendChallengeResultsByAttemptId = friends,
            seenQuestionIds = seen,
            completedQuestionIds = local.playerState.completedQuestionIds +
                remote.playerState.completedQuestionIds,
            correctlyAnsweredQuestionIds = local.playerState.correctlyAnsweredQuestionIds +
                remote.playerState.correctlyAnsweredQuestionIds,
            playedCategoryRawValues = local.playerState.playedCategoryRawValues +
                remote.playerState.playedCategoryRawValues,
            perfectDifficultyRawValues = local.playerState.perfectDifficultyRawValues +
                remote.playerState.perfectDifficultyRawValues,
        )

        return CloudPlayerState(
            playerState = canonicalize(mergedPlayer, mergedSync),
            syncState = mergedSync,
        )
    }

    fun canonicalize(
        playerState: PlayerState,
        syncState: PlayerSyncState,
    ): PlayerState {
        val lifetime = syncState.lifetimeBaseByCategory.toMutableMap()
        for (deviceTotals in syncState.lifetimeIncrementsByDevice.values) {
            for ((category, points) in deviceTotals) {
                lifetime[category] = safeAdd(lifetime[category] ?: 0, points)
            }
        }

        val totalRounds = safeAdd(
            syncState.totalRoundsBaseline,
            syncState.totalRoundIncrementsByDevice.values.sumSafely(),
        )
        val quickRounds = safeAdd(
            syncState.quickPlayRoundsBaseline,
            syncState.quickPlayRoundIncrementsByDevice.values.sumSafely(),
        )

        return playerState.copy(
            lifetimePointsByCategory = lifetime.filterValues { it > 0 },
            totalRoundsCompleted = totalRounds,
            quickPlayRoundsCompleted = quickRounds,
        )
    }

    private fun mergeSeenQuestionIds(
        local: Map<String, Set<String>>,
        remote: Map<String, Set<String>>,
    ): Map<String, Set<String>> {
        val keys = local.keys + remote.keys
        return keys.associateWith { key -> local[key].orEmpty() + remote[key].orEmpty() }
    }

    private fun mergeDailyResults(
        local: Map<Int, DailyResult>,
        remote: Map<Int, DailyResult>,
    ): Map<Int, DailyResult> {
        val keys = local.keys + remote.keys
        return keys.associateWith { day ->
            val a = local[day]
            val b = remote[day]
            when {
                a == null -> requireNotNull(b)
                b == null -> a
                else -> earliestDailyResult(a, b)
            }
        }
    }

    private fun mergeFriendResults(
        local: Map<String, FriendChallengeResult>,
        remote: Map<String, FriendChallengeResult>,
    ): Map<String, FriendChallengeResult> {
        val keys = local.keys + remote.keys
        return keys.associateWith { attemptId ->
            val a = local[attemptId]
            val b = remote[attemptId]
            when {
                a == null -> requireNotNull(b)
                b == null -> a
                else -> earliestFriendResult(a, b)
            }
        }
    }

    private fun mergeQuickPlayResults(
        local: List<QuickPlayResult>,
        remote: List<QuickPlayResult>,
    ): List<QuickPlayResult> {
        val byId = mutableMapOf<String, QuickPlayResult>()
        (local + remote).forEach { candidate ->
            val existing = byId[candidate.id]
            byId[candidate.id] = if (existing == null) {
                candidate
            } else {
                earliestQuickPlayResult(existing, candidate)
            }
        }
        return byId.values
            .sortedWith(compareByDescending<QuickPlayResult> { it.dateMillis }.thenBy { it.id })
            .take(20)
    }

    private fun earliestCategoryResult(
        a: CategoryRoundResult,
        b: CategoryRoundResult,
    ): CategoryRoundResult = if (categoryResultKey(a) <= categoryResultKey(b)) a else b

    private fun earliestDailyResult(a: DailyResult, b: DailyResult): DailyResult =
        if (dailyResultKey(a) <= dailyResultKey(b)) a else b

    private fun earliestFriendResult(
        a: FriendChallengeResult,
        b: FriendChallengeResult,
    ): FriendChallengeResult = if (friendResultKey(a) <= friendResultKey(b)) a else b

    private fun earliestQuickPlayResult(
        a: QuickPlayResult,
        b: QuickPlayResult,
    ): QuickPlayResult = if (quickPlayResultKey(a) <= quickPlayResultKey(b)) a else b

    private fun categoryResultKey(value: CategoryRoundResult): String =
        "%020d|%s|%s|%010d|%010d".format(
            value.dateMillis,
            value.category,
            value.difficulty,
            value.score,
            value.total,
        )

    private fun dailyResultKey(value: DailyResult): String =
        "%020d|%010d|%010d|%010d|%s".format(
            value.dateMillis,
            value.score,
            value.total,
            value.points,
            value.outcomes.joinToString(separator = "") { if (it) "1" else "0" },
        )

    private fun friendResultKey(value: FriendChallengeResult): String =
        "%020d|%s|%010d|%010d|%010d|%s|%s".format(
            value.dateMillis,
            value.code.attemptId,
            value.score,
            value.total,
            value.points,
            if (value.createdChallenge) "1" else "0",
            value.outcomes.joinToString(separator = "") { if (it) "1" else "0" },
        )

    private fun quickPlayResultKey(value: QuickPlayResult): String =
        "%020d|%s|%010d|%010d|%010d|%s".format(
            value.dateMillis,
            value.id,
            value.score,
            value.total,
            value.points,
            value.outcomes.joinToString(separator = "") { if (it) "1" else "0" },
        )

    private fun mergeMaxMaps(
        local: Map<String, Int>,
        remote: Map<String, Int>,
    ): Map<String, Int> {
        val keys = local.keys + remote.keys
        return keys.associateWith { key -> maxOf(local[key] ?: 0, remote[key] ?: 0) }
    }

    private fun mergeNestedMaxMaps(
        local: Map<String, Map<String, Int>>,
        remote: Map<String, Map<String, Int>>,
    ): Map<String, Map<String, Int>> {
        val devices = local.keys + remote.keys
        return devices.associateWith { device ->
            mergeMaxMaps(local[device].orEmpty(), remote[device].orEmpty())
        }
    }

    private fun safeAdd(a: Int, b: Int): Int =
        (a.toLong() + b.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun Collection<Int>.sumSafely(): Int =
        fold(0) { total, value -> safeAdd(total, value) }

    private inline fun <T> Collection<T>.sumOfSafely(selector: (T) -> Int): Int =
        fold(0) { total, value -> safeAdd(total, selector(value)) }
}

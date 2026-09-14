package com.rsm.eztrivia.playgames

import android.app.Activity
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.Snapshot
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import com.rsm.eztrivia.data.CloudPlayerState
import com.rsm.eztrivia.data.PlayerStateStore
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

data class PlayGamesSavedState(
    val isSyncing: Boolean = false,
    val lastSyncedAtMillis: Long? = null,
    val errorMessage: String? = null,
)

/**
 * Conflict-safe Google Play Games Saved Games bridge.
 *
 * Requests are coalesced because PlayerState changes throughout a round. Core
 * play never waits for cloud I/O: failures are surfaced in Settings and the
 * next authenticated state change simply retries. Manual conflict resolution is
 * used so independent offline progress from two devices is merged rather than
 * letting the most-recent device overwrite the other.
 */
class PlayGamesSavedStateManager(
    activity: Activity,
    private val playerStateStore: PlayerStateStore,
    private val scope: CoroutineScope,
) {
    private val snapshotsClient = PlayGames.getSnapshotsClient(activity)
    private val requests = Channel<Unit>(capacity = Channel.CONFLATED)
    private val syncMutex = Mutex()
    private val _state = MutableStateFlow(PlayGamesSavedState())
    val state: StateFlow<PlayGamesSavedState> = _state.asStateFlow()

    private val worker: Job = scope.launch {
        for (ignored in requests) {
            // Let bursts of answer/history writes settle before opening the
            // cloud slot. Requests arriving during the delay are conflated.
            delay(AUTO_SYNC_DELAY_MILLIS)
            while (requests.tryReceive().isSuccess) {
                // Drain the conflated pending request before this sync.
            }
            syncInternal()
        }
    }

    fun requestSync() {
        requests.trySend(Unit)
    }

    fun syncNow() {
        scope.launch { syncInternal() }
    }

    fun close() {
        requests.close()
        worker.cancel()
    }

    private suspend fun syncInternal() {
        syncMutex.withLock {
            _state.value = _state.value.copy(isSyncing = true, errorMessage = null)
            try {
                var openResult = snapshotsClient.open(
                    SNAPSHOT_NAME,
                    true,
                    SnapshotsClient.RESOLUTION_POLICY_MANUAL,
                ).await()

                var conflictCount = 0
                while (openResult.isConflict) {
                    conflictCount += 1
                    if (conflictCount > MAX_CONFLICT_RESOLUTIONS) {
                        throw IOException("Google Play Games returned repeated save conflicts.")
                    }

                    val conflict = openResult.conflict
                    val server = decodeSnapshot(conflict.snapshot)
                    val conflicting = decodeSnapshot(conflict.conflictingSnapshot)

                    // Merge both conflict sides into the newest disk-backed
                    // local state. mergeCloudEnvelope is associative/idempotent
                    // for additive counters and set/history facts.
                    var merged = playerStateStore.mergeCloudEnvelope(server)
                    merged = playerStateStore.mergeCloudEnvelope(conflicting)

                    val resolutionContents = conflict.resolutionSnapshotContents
                    val wroteResolution = resolutionContents.writeBytes(
                        playerStateStore.encodeCloudEnvelope(merged)
                    )
                    if (!wroteResolution) {
                        throw IOException("Could not write the resolved Play Games save.")
                    }

                    openResult = snapshotsClient.resolveConflict(
                        conflict.conflictId,
                        conflict.snapshot.metadata.snapshotId,
                        metadataChange(merged),
                        resolutionContents,
                    ).await()
                }

                val openedSnapshot = openResult.data
                val remote = decodeSnapshot(openedSnapshot)
                val merged = playerStateStore.mergeCloudEnvelope(remote)
                val wroteSnapshot = openedSnapshot.snapshotContents.writeBytes(
                    playerStateStore.encodeCloudEnvelope(merged)
                )
                if (!wroteSnapshot) {
                    throw IOException("Could not write the Play Games save.")
                }

                snapshotsClient.commitAndClose(openedSnapshot, metadataChange(merged)).await()
                _state.value = PlayGamesSavedState(
                    isSyncing = false,
                    lastSyncedAtMillis = System.currentTimeMillis(),
                    errorMessage = null,
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    isSyncing = false,
                    errorMessage = error.localizedMessage ?: "Google Play Games progress sync failed.",
                )
            }
        }
    }

    private fun decodeSnapshot(snapshot: Snapshot): CloudPlayerState {
        val bytes = snapshot.snapshotContents.readFully()
        return playerStateStore.decodeCloudEnvelope(bytes)
            ?: throw IOException(
                "Saved progress uses an unsupported or unreadable format; local progress was left unchanged."
            )
    }

    private fun metadataChange(envelope: CloudPlayerState): SnapshotMetadataChange =
        SnapshotMetadataChange.Builder()
            .setDescription("EZ Trivia player progress")
            .setProgressValue(envelope.playerState.totalRoundsCompleted.toLong())
            .build()

    private companion object {
        const val SNAPSHOT_NAME = "eztrivia-player-state-v1"
        const val AUTO_SYNC_DELAY_MILLIS = 2_000L
        const val MAX_CONFLICT_RESOLUTIONS = 8
    }
}

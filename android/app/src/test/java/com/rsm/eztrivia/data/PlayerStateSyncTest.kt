package com.rsm.eztrivia.data

import com.rsm.eztrivia.model.FriendChallengeCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerStateSyncTest {
    @Test
    fun legacyScalarsBecomeSharedBaseline() {
        val legacy = PlayerState(
            lifetimePointsByCategory = mapOf("History" to 120),
            totalRoundsCompleted = 7,
            quickPlayRoundsCompleted = 2,
        )

        val envelope = PlayerStateSync.prepareEnvelope(legacy, PlayerSyncState())

        assertTrue(envelope.syncState.initialized)
        assertEquals(120, envelope.syncState.lifetimeBaseByCategory["History"])
        assertEquals(7, envelope.syncState.totalRoundsBaseline)
        assertEquals(2, envelope.syncState.quickPlayRoundsBaseline)
        assertTrue(envelope.syncState.lifetimeIncrementsByDevice.isEmpty())
        assertEquals(legacy.lifetimePointsByCategory, envelope.playerState.lifetimePointsByCategory)
        assertEquals(7, envelope.playerState.totalRoundsCompleted)
        assertEquals(2, envelope.playerState.quickPlayRoundsCompleted)
    }

    @Test
    fun perDeviceProgressAddsAcrossDevicesWithoutDoubleCountingRepeatedMerge() {
        val legacy = PlayerState(
            lifetimePointsByCategory = mapOf("History" to 100),
            totalRoundsCompleted = 5,
        )
        val baseSync = PlayerStateSync.prepareEnvelope(legacy, PlayerSyncState()).syncState

        val syncA = PlayerStateSync.incrementCompletedRound(
            PlayerStateSync.addLifetimePoints(baseSync, "device-a", "History", 20),
            installationId = "device-a",
            quickPlay = false,
        )
        val stateA = PlayerStateSync.canonicalize(legacy, syncA)
        val envelopeA = CloudPlayerState(playerState = stateA, syncState = syncA)

        val syncB = PlayerStateSync.incrementCompletedRound(
            PlayerStateSync.addLifetimePoints(baseSync, "device-b", "History", 30),
            installationId = "device-b",
            quickPlay = false,
        )
        val stateB = PlayerStateSync.canonicalize(legacy, syncB)
        val envelopeB = CloudPlayerState(playerState = stateB, syncState = syncB)

        val merged = PlayerStateSync.merge(envelopeA, envelopeB)
        assertEquals(150, merged.playerState.lifetimePointsByCategory["History"])
        assertEquals(7, merged.playerState.totalRoundsCompleted)
        assertEquals(20, merged.syncState.lifetimeIncrementsByDevice["device-a"]?.get("History"))
        assertEquals(30, merged.syncState.lifetimeIncrementsByDevice["device-b"]?.get("History"))

        val mergedAgain = PlayerStateSync.merge(merged, envelopeA)
        assertEquals(150, mergedAgain.playerState.lifetimePointsByCategory["History"])
        assertEquals(7, mergedAgain.playerState.totalRoundsCompleted)
    }

    @Test
    fun newerClearPreventsOldRecentAndSeenStateFromReturning() {
        val remote = CloudPlayerState(
            playerState = PlayerState(
                recentCategoryResults = listOf(
                    CategoryRoundResult(
                        id = "old-round",
                        category = "History",
                        difficulty = "Easy",
                        score = 5,
                        total = 10,
                        dateMillis = 100L,
                    )
                ),
                seenQuestionIds = mapOf("History-Easy" to setOf("q1", "q2")),
            ),
            syncState = PlayerSyncState(initialized = true),
        )
        val local = CloudPlayerState(
            playerState = PlayerState(),
            syncState = PlayerSyncState(
                initialized = true,
                recentHistoryResetAtMillis = 200L,
                seenQuestionsResetAtMillis = 200L,
            ),
        )

        val merged = PlayerStateSync.merge(local, remote)

        assertTrue(merged.playerState.recentCategoryResults.isEmpty())
        assertTrue(merged.playerState.seenQuestionIds.isEmpty())
        assertEquals(200L, merged.syncState.recentHistoryResetAtMillis)
        assertEquals(200L, merged.syncState.seenQuestionsResetAtMillis)
    }

    @Test
    fun oneAttemptRecordsKeepEarliestResult() {
        val code = FriendChallengeCode(seed = 42UL, targetScore = 7, targetPoints = 70)
        val localDaily = DailyResult(
            day = 300,
            score = 8,
            total = 10,
            points = 80,
            outcomes = listOf(true, true),
            dateMillis = 200L,
        )
        val remoteDaily = localDaily.copy(score = 4, points = 40, dateMillis = 100L)
        val localFriend = FriendChallengeResult(
            code = code,
            score = 8,
            total = 10,
            points = 80,
            outcomes = listOf(true, false),
            createdChallenge = true,
            dateMillis = 300L,
        )
        val remoteFriend = localFriend.copy(score = 6, points = 60, dateMillis = 250L)

        val local = CloudPlayerState(
            playerState = PlayerState(
                dailyResultsByDay = mapOf(300 to localDaily),
                friendChallengeResultsByAttemptId = mapOf(code.attemptId to localFriend),
            ),
            syncState = PlayerSyncState(initialized = true),
        )
        val remote = CloudPlayerState(
            playerState = PlayerState(
                dailyResultsByDay = mapOf(300 to remoteDaily),
                friendChallengeResultsByAttemptId = mapOf(code.attemptId to remoteFriend),
            ),
            syncState = PlayerSyncState(initialized = true),
        )

        val merged = PlayerStateSync.merge(local, remote)

        assertEquals(remoteDaily, merged.playerState.dailyResultsByDay[300])
        assertEquals(remoteFriend, merged.playerState.friendChallengeResultsByAttemptId[code.attemptId])
    }

    @Test
    fun quickPlayHistoryDeduplicatesAndCapsAtTwenty() {
        val localResults = (0 until 15).map { index -> quick("q-$index", index.toLong()) }
        val remoteResults = (10 until 30).map { index -> quick("q-$index", index.toLong()) }
        val local = CloudPlayerState(
            playerState = PlayerState(quickPlayResults = localResults),
            syncState = PlayerSyncState(initialized = true),
        )
        val remote = CloudPlayerState(
            playerState = PlayerState(quickPlayResults = remoteResults),
            syncState = PlayerSyncState(initialized = true),
        )

        val merged = PlayerStateSync.merge(local, remote)

        assertEquals(20, merged.playerState.quickPlayResults.size)
        assertEquals(20, merged.playerState.quickPlayResults.map(QuickPlayResult::id).toSet().size)
        assertEquals("q-29", merged.playerState.quickPlayResults.first().id)
        assertFalse("q-0" in merged.playerState.quickPlayResults.map(QuickPlayResult::id))
    }

    @Test
    fun scalarProgressFromOlderBuildIsFoldedBackIntoBaseline() {
        val sync = PlayerSyncState(
            initialized = true,
            lifetimeBaseByCategory = mapOf("History" to 100),
            lifetimeIncrementsByDevice = mapOf("device-a" to mapOf("History" to 20)),
            totalRoundsBaseline = 5,
            totalRoundIncrementsByDevice = mapOf("device-a" to 1),
        )
        // Simulates an older build making progress while the second sync key is
        // left untouched.
        val playerFromOlderBuild = PlayerState(
            lifetimePointsByCategory = mapOf("History" to 150),
            totalRoundsCompleted = 8,
        )

        val normalized = PlayerStateSync.prepareEnvelope(playerFromOlderBuild, sync)

        assertEquals(130, normalized.syncState.lifetimeBaseByCategory["History"])
        assertEquals(7, normalized.syncState.totalRoundsBaseline)
        assertEquals(150, normalized.playerState.lifetimePointsByCategory["History"])
        assertEquals(8, normalized.playerState.totalRoundsCompleted)
    }

    private fun quick(id: String, dateMillis: Long) = QuickPlayResult(
        id = id,
        score = 5,
        total = 10,
        points = 50,
        outcomes = listOf(true, false),
        dateMillis = dateMillis,
    )
}

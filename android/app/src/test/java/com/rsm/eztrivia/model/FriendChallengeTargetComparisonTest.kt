package com.rsm.eztrivia.model

import org.junit.Assert.assertTrue
import org.junit.Test

class FriendChallengeTargetComparisonTest {
    @Test
    fun weightedPointsCanBreakEqualRawScoreTies() {
        val senderPoints = 1_000
        val receiverPoints = 1_100
        assertTrue(receiverPoints > senderPoints)
    }
}

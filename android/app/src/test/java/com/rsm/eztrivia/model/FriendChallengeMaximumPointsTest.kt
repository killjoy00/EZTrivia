package com.rsm.eztrivia.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FriendChallengeMaximumPointsTest {
    @Test
    fun maximumPointsMatchesTheFrozenV3Ramp() {
        assertEquals(
            FriendChallenge.MAXIMUM_POINTS,
            FriendChallenge.difficultyRamp.sumOf { Scoring.points(it) },
        )
    }
}

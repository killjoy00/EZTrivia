package com.rsm.eztrivia.model

import com.rsm.eztrivia.data.FriendChallengeResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class FriendChallengeSerializationTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun friendChallengeResultRoundTripsThroughPlayerStateSerializationShape() {
        val code = FriendChallengeCode(seed = ULong.MAX_VALUE, targetScore = 8, targetPoints = 1_350)
        val result = FriendChallengeResult(
            code = code,
            score = 9,
            total = 10,
            points = 1_500,
            outcomes = List(10) { it < 9 },
            createdChallenge = false,
            dateMillis = 1234,
        )

        val encoded = json.encodeToString(result)
        val decoded = json.decodeFromString<FriendChallengeResult>(encoded)
        assertEquals(result, decoded)
        assertEquals(code.displayString, decoded.code.displayString)
    }
}

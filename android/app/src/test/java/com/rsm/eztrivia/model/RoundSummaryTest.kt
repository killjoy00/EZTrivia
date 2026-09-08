package com.rsm.eztrivia.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RoundSummaryTest {
    @Test
    fun categoryShareIsSpoilerSafeAndStable() {
        val text = RoundSummary.round(
            category = TriviaCategory.HISTORY,
            difficulty = TriviaDifficulty.HARD,
            outcomes = listOf(true, false, true, true),
        )

        assertEquals(
            "EZ Trivia — History, Hard\n🟩⬜️🟩🟩\n3/4 correct\n${RoundSummary.playStoreUrl}",
            text,
        )
        assertFalse(text.contains("answer", ignoreCase = true))
    }

    @Test
    fun quickPlayShareIncludesWeightedPoints() {
        assertEquals(
            "EZ Trivia Quick Play — 2/3\n🟩⬜️🟩\n450 points\n${RoundSummary.playStoreUrl}",
            RoundSummary.quickPlay(listOf(true, false, true), points = 450),
        )
    }
}

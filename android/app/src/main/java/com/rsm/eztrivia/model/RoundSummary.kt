package com.rsm.eztrivia.model

/** Spoiler-safe share text that mirrors the iOS result summaries. */
object RoundSummary {
    private const val correctMark = "🟩"
    private const val wrongMark = "⬜️"

    /** Stable package URL; it becomes live automatically when the Play listing exists. */
    const val playStoreUrl = "https://play.google.com/store/apps/details?id=com.rsm.eztrivia"

    fun headline(correct: Int, total: Int): String = "I scored $correct/$total on EZ Trivia"

    fun grid(outcomes: List<Boolean>): String =
        outcomes.joinToString(separator = "") { if (it) correctMark else wrongMark }

    fun round(
        category: TriviaCategory,
        difficulty: TriviaDifficulty,
        outcomes: List<Boolean>,
    ): String = listOf(
        "EZ Trivia — ${category.title}, ${difficulty.title}",
        grid(outcomes),
        "${outcomes.count { it }}/${outcomes.size} correct",
        playStoreUrl,
    ).joinToString("\n")

    fun quickPlay(outcomes: List<Boolean>, points: Int): String = listOf(
        "EZ Trivia Quick Play — ${outcomes.count { it }}/${outcomes.size}",
        grid(outcomes),
        "$points points",
        playStoreUrl,
    ).joinToString("\n")

    fun daily(
        day: Int,
        outcomes: List<Boolean>,
        points: Int,
        streak: Int,
    ): String {
        val lines = mutableListOf(
            "EZ Trivia Daily #$day — ${outcomes.count { it }}/${outcomes.size}",
            grid(outcomes),
            "$points points",
        )
        if (streak > 1) lines += "$streak day streak 🔥"
        lines += playStoreUrl
        return lines.joinToString("\n")
    }
}

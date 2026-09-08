package com.rsm.eztrivia.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Cross-platform Daily Challenge v2.
 *
 * Daily #252 (internal day 251, September 9, 2026) is the first day whose
 * selection contract is frozen across Swift and Kotlin. Android deliberately
 * does not attempt to reproduce earlier Swift-standard-library rounds.
 */
object DailyChallenge {
    const val QUESTION_COUNT = 10
    const val CROSS_PLATFORM_START_DAY = 251
    const val ALGORITHM_VERSION = 2
    const val MAXIMUM_POINTS = 1_650

    private val epoch: LocalDate = LocalDate.of(2026, 1, 1)

    val categoryRoster: List<TriviaCategory> = listOf(
        TriviaCategory.FOOTBALL,
        TriviaCategory.BASKETBALL,
        TriviaCategory.SOCCER,
        TriviaCategory.FLAGS,
        TriviaCategory.HISTORY,
        TriviaCategory.SCIENCE,
        TriviaCategory.MOVIES,
        TriviaCategory.TV,
        TriviaCategory.GEOGRAPHY,
        TriviaCategory.MUSIC,
        TriviaCategory.ANIMALS,
        TriviaCategory.FOOD,
        TriviaCategory.LITERATURE,
        TriviaCategory.ART,
        TriviaCategory.MYTHOLOGY,
        TriviaCategory.VIDEO_GAMES,
    )

    val difficultyRamp: List<TriviaDifficulty> = listOf(
        TriviaDifficulty.EASY,
        TriviaDifficulty.EASY,
        TriviaDifficulty.EASY,
        TriviaDifficulty.MEDIUM,
        TriviaDifficulty.MEDIUM,
        TriviaDifficulty.MEDIUM,
        TriviaDifficulty.MEDIUM,
        TriviaDifficulty.HARD,
        TriviaDifficulty.HARD,
        TriviaDifficulty.HARD,
    )

    init {
        check(difficultyRamp.sumOf(Scoring::points) == MAXIMUM_POINTS)
    }

    fun day(date: LocalDate = LocalDate.now()): Int =
        ChronoUnit.DAYS.between(epoch, date).toInt()

    fun displayNumber(day: Int): Int = day + 1

    fun isCrossPlatformDay(day: Int): Boolean = day >= CROSS_PLATFORM_START_DAY

    fun seed(day: Int): ULong {
        val base = (day.toLong() + 0x1_0000L).toULong()
        return (base * 0x9E3779B97F4A7C15UL) xor 0x4441494C59212121UL
    }

    fun challenge(day: Int, bank: List<TriviaQuestion>): List<TriviaQuestion> {
        require(isCrossPlatformDay(day)) {
            "Android Daily parity begins at day $CROSS_PLATFORM_START_DAY"
        }

        val generator = SeededGenerator(seed(day))
        val categories = categoryRoster.deterministicallyShuffled(generator).take(QUESTION_COUNT)
        val usedIds = mutableSetOf<String>()
        val questions = mutableListOf<TriviaQuestion>()

        for (slot in 0 until minOf(QUESTION_COUNT, categories.size)) {
            val category = categories[slot]
            val difficulty = difficultyRamp[slot]
            val pool = bank.filter { question ->
                question.category == category &&
                    question.difficulty == difficulty &&
                    question.id !in usedIds
            }
            val fallback = bank.filter { it.id !in usedIds }
            val candidates = if (pool.isEmpty()) fallback else pool
            if (candidates.isEmpty()) continue

            val index = (generator.nextULong() % candidates.size.toULong()).toInt()
            val picked = candidates[index]
            usedIds += picked.id
            questions += SeededQuestionPresenter.presenting(picked, bank, generator)
        }

        return questions
    }
}

object DailyStreak {
    /** Matches the iOS rule: yesterday's streak remains alive until today is fully missed. */
    fun current(playedDays: Set<Int>, today: Int): Int {
        var day = if (today in playedDays) today else today - 1
        if (day !in playedDays) return 0

        var length = 0
        while (day in playedDays) {
            length += 1
            day -= 1
        }
        return length
    }

    fun dayAtRisk(
        playedDays: Set<Int>,
        today: Int,
        minimumStreak: Int = 2,
    ): Pair<Int, Int>? {
        val streak = current(playedDays, today)
        if (streak < minimumStreak) return null
        return (if (today in playedDays) today + 1 else today) to streak
    }
}

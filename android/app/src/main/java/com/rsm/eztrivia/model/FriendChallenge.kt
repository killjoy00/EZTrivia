package com.rsm.eztrivia.model

import java.net.URI
import java.net.URLDecoder

/**
 * Android implementation of Friend Challenge v3.
 *
 * Every random choice mirrors the repository-owned Swift algorithm: category
 * order, question selection, ordinary answer order, and flag distractor redraw.
 * Do not replace these helpers with Kotlin's shuffled/random APIs; their exact
 * permutation is not a cross-platform contract.
 */
object FriendChallenge {
    const val QUESTION_COUNT = 10
    const val CODE_VERSION = 3
    const val MAXIMUM_POINTS = 1_650

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
        check(CODE_VERSION == FriendChallengeCode.CODE_VERSION)
        check(difficultyRamp.sumOf { Scoring.points(it) } == MAXIMUM_POINTS)
    }

    fun challenge(seed: ULong, bank: List<TriviaQuestion>): List<TriviaQuestion> {
        val generator = SeededGenerator(seed xor 0x465249454E442121UL)
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
            if (pool.isEmpty()) continue

            val index = (generator.nextULong() % pool.size.toULong()).toInt()
            val picked = pool[index]
            usedIds += picked.id
            questions += SeededQuestionPresenter.presenting(picked, bank, generator)
        }

        return questions
    }
}

/** Deep-link and share-link handoff shared with the iOS client. */
object FriendChallengeLink {
    const val CUSTOM_SCHEME = "eztrivia"
    const val CUSTOM_HOST = "challenge"
    const val WEB_BASE_URL = "https://killjoy00.github.io/EZTrivia/challenge.html"

    fun customUrl(code: FriendChallengeCode): String =
        "$CUSTOM_SCHEME://$CUSTOM_HOST/${code.displayString}"

    fun webUrl(code: FriendChallengeCode): String =
        "$WEB_BASE_URL?code=${code.displayString}"

    fun codeFrom(rawValue: String): FriendChallengeCode? {
        val candidate = rawCodeCandidate(rawValue) ?: return null
        return FriendChallengeCode.parse(candidate)
    }

    fun rejectionReason(rawValue: String): FriendChallengeCode.RejectionReason? {
        val candidate = rawCodeCandidate(rawValue) ?: rawValue.trim()
        return FriendChallengeCode.rejectionReason(candidate)
    }

    private fun rawCodeCandidate(rawValue: String): String? {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) return null
        FriendChallengeCode.parse(trimmed)?.let { return it.displayString }

        val uri = runCatching { URI(trimmed) }.getOrNull()
        if (uri == null || uri.scheme == null) return trimmed

        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()

        if (scheme == CUSTOM_SCHEME && host == CUSTOM_HOST) {
            val rawCode = uri.rawPath.orEmpty().trim('/')
            return rawCode.takeIf { it.isNotEmpty() }?.let(::decode)
        }

        if (
            scheme == "https" &&
            host == "killjoy00.github.io" &&
            uri.path?.lowercase() == "/eztrivia/challenge.html"
        ) {
            return queryValue(uri.rawQuery, "code")
        }

        return null
    }

    private fun queryValue(rawQuery: String?, name: String): String? {
        if (rawQuery.isNullOrBlank()) return null
        return rawQuery.split('&').firstNotNullOfOrNull { item ->
            val parts = item.split('=', limit = 2)
            if (parts.size != 2 || decode(parts[0]) != name) null else decode(parts[1])
        }
    }

    @Suppress("DEPRECATION")
    private fun decode(value: String): String = URLDecoder.decode(value, "UTF-8")
}

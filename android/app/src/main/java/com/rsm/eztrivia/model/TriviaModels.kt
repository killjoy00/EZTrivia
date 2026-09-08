package com.rsm.eztrivia.model

import kotlin.random.Random

enum class TriviaCategory(val wireName: String, val title: String) {
    FOOTBALL("football", "Football"),
    BASKETBALL("basketball", "Basketball"),
    SOCCER("soccer", "Soccer"),
    FLAGS("flags", "World Flags"),
    HISTORY("history", "History"),
    SCIENCE("science", "Science"),
    MOVIES("movies", "Movies"),
    TV("tv", "TV"),
    GEOGRAPHY("geography", "Geography"),
    MUSIC("music", "Music"),
    ANIMALS("animals", "Animals"),
    FOOD("food", "Food & Drink"),
    LITERATURE("literature", "Books & Literature"),
    ART("art", "Art & Architecture"),
    MYTHOLOGY("mythology", "Mythology & Legends"),
    VIDEO_GAMES("videoGames", "Video Games");

    companion object {
        private val byWireName = entries.associateBy(TriviaCategory::wireName)

        fun fromWireName(value: String): TriviaCategory =
            requireNotNull(byWireName[value]) { "Unknown trivia category: $value" }
    }
}

enum class TriviaDifficulty(val wireName: String, val title: String) {
    EASY("easy", "Easy"),
    MEDIUM("medium", "Medium"),
    HARD("hard", "Hard");

    companion object {
        private val byWireName = entries.associateBy(TriviaDifficulty::wireName)

        fun fromWireName(value: String): TriviaDifficulty =
            requireNotNull(byWireName[value]) { "Unknown trivia difficulty: $value" }
    }
}

data class TriviaQuestion(
    val id: String,
    val category: TriviaCategory,
    val prompt: String,
    val difficulty: TriviaDifficulty,
    val visual: String?,
    val answers: List<String>,
    val correctAnswerIndex: Int,
    val explanation: String,
) {
    init {
        require(answers.size == 4) { "A trivia question must have exactly four answers" }
        require(correctAnswerIndex in answers.indices) { "Correct answer index is out of bounds" }
    }

    fun shuffledAnswers(random: Random = Random.Default): TriviaQuestion {
        val indexedAnswers = answers.mapIndexed { index, answer -> index to answer }.shuffled(random)
        return copy(
            answers = indexedAnswers.map { it.second },
            correctAnswerIndex = indexedAnswers.indexOfFirst { it.first == correctAnswerIndex },
        )
    }
}

object Scoring {
    fun points(difficulty: TriviaDifficulty): Int = when (difficulty) {
        TriviaDifficulty.EASY -> 100
        TriviaDifficulty.MEDIUM -> 150
        TriviaDifficulty.HARD -> 250
    }

    fun points(question: TriviaQuestion): Int = points(question.difficulty)
}

class TriviaEngine(questions: List<TriviaQuestion>) {
    val questions: List<TriviaQuestion> = questions.toList()
    var currentIndex: Int = 0
        private set
    var score: Int = 0
        private set
    var points: Int = 0
        private set
    var selectedAnswerIndex: Int? = null
        private set
    private val mutableOutcomes = mutableListOf<Boolean>()
    val outcomes: List<Boolean> get() = mutableOutcomes.toList()

    val currentQuestion: TriviaQuestion?
        get() = questions.getOrNull(currentIndex)
    val isRoundComplete: Boolean
        get() = currentIndex >= questions.size
    val progress: Double
        get() = if (questions.isEmpty()) 0.0 else (currentIndex.toDouble() / questions.size).coerceAtMost(1.0)

    fun answer(index: Int): Boolean {
        val question = currentQuestion ?: return false
        if (selectedAnswerIndex != null || index !in question.answers.indices) return false

        selectedAnswerIndex = index
        val correct = index == question.correctAnswerIndex
        if (correct) {
            score += 1
            points += Scoring.points(question)
        }
        mutableOutcomes += correct
        return correct
    }

    fun advance(): Boolean {
        if (selectedAnswerIndex == null || isRoundComplete) return false
        currentIndex += 1
        selectedAnswerIndex = null
        return true
    }
}

object QuestionPicker {
    val quickPlayDifficultyRamp = listOf(
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

    fun quickPlayRound(
        bank: List<TriviaQuestion>,
        count: Int = 10,
        excludedIds: Set<String> = emptySet(),
        random: Random = Random.Default,
    ): List<TriviaQuestion> {
        val requestedCount = count.coerceIn(0, TriviaCategory.entries.size)
        if (requestedCount == 0) return emptyList()

        val categories = TriviaCategory.entries.shuffled(random).take(requestedCount)
        val chosenIds = mutableSetOf<String>()

        return categories.mapIndexedNotNull { slot, category ->
            val difficulty = quickPlayDifficultyRamp[slot % quickPlayDifficultyRamp.size]
            val matching = bank.filter {
                it.category == category && it.difficulty == difficulty && it.id !in chosenIds
            }
            val unseen = matching.filter { it.id !in excludedIds }
            val pool = if (unseen.isEmpty()) matching else unseen
            val picked = pool.randomOrNull(random) ?: return@mapIndexedNotNull null
            chosenIds += picked.id
            picked.shuffledAnswers(random)
        }
    }
}

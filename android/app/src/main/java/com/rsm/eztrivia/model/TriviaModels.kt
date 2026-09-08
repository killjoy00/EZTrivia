package com.rsm.eztrivia.model

import kotlin.random.Random

enum class TriviaCategory(
    val wireName: String,
    val title: String,
    val subtitle: String,
) {
    FOOTBALL("football", "Football", "Touchdowns & legends"),
    BASKETBALL("basketball", "Basketball", "Hoops, teams & legends"),
    SOCCER("soccer", "Soccer", "The beautiful game"),
    FLAGS("flags", "World Flags", "Colors around the globe"),
    HISTORY("history", "History", "People who shaped our world"),
    SCIENCE("science", "Science", "Nature, space & discovery"),
    MOVIES("movies", "Movies", "Big-screen favorites"),
    TV("tv", "TV", "Small-screen favorites"),
    GEOGRAPHY("geography", "Geography", "Places near & far"),
    MUSIC("music", "Music", "Artists, songs & sounds"),
    ANIMALS("animals", "Animals", "Wildlife & nature"),
    FOOD("food", "Food & Drink", "Flavors of the world"),
    LITERATURE("literature", "Books & Literature", "Books, authors & stories"),
    ART("art", "Art & Architecture", "Masterpieces & monuments"),
    MYTHOLOGY("mythology", "Mythology & Legends", "Gods, heroes & folklore"),
    VIDEO_GAMES("videoGames", "Video Games", "Consoles, classics & studios");

    companion object {
        private val byWireName = entries.associateBy(TriviaCategory::wireName)

        fun fromWireName(value: String): TriviaCategory =
            requireNotNull(byWireName[value]) { "Unknown trivia category: $value" }
    }
}

enum class TriviaDifficulty(
    val wireName: String,
    val title: String,
    val subtitle: String,
) {
    EASY("easy", "Easy", "A friendly warm-up"),
    MEDIUM("medium", "Medium", "A balanced challenge"),
    HARD("hard", "Hard", "For trivia experts");

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
    val flagCode: String? = null,
    val confusableFlagCodes: Set<String> = emptySet(),
) {
    init {
        require(answers.size == 4) { "A trivia question must have exactly four answers" }
        require(correctAnswerIndex in answers.indices) { "Correct answer index is out of bounds" }
        require(category == TriviaCategory.FLAGS || flagCode == null) {
            "Only flag questions may carry a flag code"
        }
    }

    val correctAnswer: String
        get() = answers[correctAnswerIndex]

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

    fun round(
        bank: List<TriviaQuestion>,
        category: TriviaCategory,
        difficulty: TriviaDifficulty = TriviaDifficulty.EASY,
        count: Int = 10,
        excludedIds: Set<String> = emptySet(),
        random: Random = Random.Default,
    ): List<TriviaQuestion> {
        if (count <= 0) return emptyList()

        val matching = bank.filter { it.category == category && it.difficulty == difficulty }
        val unseen = matching.filter { it.id !in excludedIds }
        val pool = unseen.shuffled(random).toMutableList()

        if (pool.size < count) {
            val chosen = pool.mapTo(mutableSetOf()) { it.id }
            pool += matching.filter { it.id !in chosen }.shuffled(random)
        }

        return pool.take(count).map { it.shuffledAnswers(random) }
    }

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

    fun availableCount(
        bank: List<TriviaQuestion>,
        category: TriviaCategory,
        difficulty: TriviaDifficulty,
    ): Int = bank.count { it.category == category && it.difficulty == difficulty }
}

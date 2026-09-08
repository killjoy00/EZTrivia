package com.rsm.eztrivia.model

/**
 * Repository-owned question presentation used by every cross-platform seeded mode.
 *
 * Ordinary answers use deterministic Fisher-Yates. Flag questions redraw three
 * same-tier, non-confusable distractors and then deterministically shuffle all
 * four choices. This mirrors QuestionBank.presenting in the Swift core.
 */
object SeededQuestionPresenter {
    fun presenting(
        question: TriviaQuestion,
        bank: List<TriviaQuestion>,
        generator: SeededGenerator,
    ): TriviaQuestion = if (question.category == TriviaCategory.FLAGS && question.flagCode != null) {
        redrawFlagQuestion(question, bank, generator)
    } else {
        val correct = question.correctAnswer
        val answers = question.answers.deterministicallyShuffled(generator)
        question.copy(
            answers = answers,
            correctAnswerIndex = answers.indexOf(correct).takeIf { it >= 0 } ?: question.correctAnswerIndex,
        )
    }

    private fun redrawFlagQuestion(
        question: TriviaQuestion,
        bank: List<TriviaQuestion>,
        generator: SeededGenerator,
    ): TriviaQuestion {
        val sameTier = bank.filter { candidate ->
            candidate.category == TriviaCategory.FLAGS &&
                candidate.difficulty == question.difficulty &&
                candidate.flagCode != null &&
                mayAppearTogether(question, candidate)
        }
        val chosen = sameTier.deterministicallyShuffled(generator).take(3).toMutableList()

        if (chosen.size < 3) {
            val taken = chosen.mapNotNull(TriviaQuestion::flagCode).toSet()
            val filler = bank.filter { candidate ->
                candidate.category == TriviaCategory.FLAGS &&
                    candidate.flagCode != null &&
                    candidate.flagCode !in taken &&
                    mayAppearTogether(question, candidate)
            }
            chosen += filler.deterministicallyShuffled(generator).take(3 - chosen.size)
        }

        val options = (chosen + question).deterministicallyShuffled(generator)
        val correctIndex = options.indexOfFirst { it.flagCode == question.flagCode }
        check(correctIndex >= 0) { "Flag options lost the correct answer for ${question.id}" }

        return question.copy(
            answers = options.map(TriviaQuestion::correctAnswer),
            correctAnswerIndex = correctIndex,
        )
    }

    private fun mayAppearTogether(a: TriviaQuestion, b: TriviaQuestion): Boolean {
        val aCode = a.flagCode ?: return false
        val bCode = b.flagCode ?: return false
        return aCode != bCode &&
            bCode !in a.confusableFlagCodes &&
            aCode !in b.confusableFlagCodes
    }
}

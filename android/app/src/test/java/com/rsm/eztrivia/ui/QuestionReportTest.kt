package com.rsm.eztrivia.ui

import com.rsm.eztrivia.model.TriviaCategory
import com.rsm.eztrivia.model.TriviaDifficulty
import com.rsm.eztrivia.model.TriviaQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionReportTest {
    private val question = TriviaQuestion(
        id = "science-42",
        category = TriviaCategory.SCIENCE,
        prompt = "What planet is nearest to the Sun?",
        difficulty = TriviaDifficulty.EASY,
        visual = null,
        answers = listOf("Venus", "Mercury", "Mars", "Earth"),
        correctAnswerIndex = 1,
        explanation = "Mercury is closest.",
    )

    @Test
    fun reportIncludesSelectedCorrectAndStableQuestionDetails() {
        val draft = questionReportDraft(question, selectedAnswerIndex = 0, version = "1.5 (8)")
        assertEquals("EZ Trivia question report — science-42", draft.subject)
        assertTrue(draft.body.contains("ID: science-42"))
        assertTrue(draft.body.contains("Category: Science"))
        assertTrue(draft.body.contains("Difficulty: Easy"))
        assertTrue(draft.body.contains("Selected answer: Venus"))
        assertTrue(draft.body.contains("Correct answer: Mercury"))
        assertTrue(draft.body.contains("Explanation: Mercury is closest."))
        assertTrue(draft.body.contains("App version: 1.5 (8)"))
    }

    @Test
    fun reportHandlesUnansweredAndOutOfRangeIndexes() {
        assertTrue(questionReportDraft(question, null, "1.5").body.contains("Selected answer: Not recorded"))
        assertTrue(questionReportDraft(question, 99, "1.5").body.contains("Selected answer: Not recorded"))
    }
}

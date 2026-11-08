package com.rsm.eztrivia.model

import com.rsm.eztrivia.data.QuestionCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionContextTest {
    @Test
    fun soccerLeaguePointsQuestionNamesItsSport() {
        val catalogFile = sequenceOf(
            File("build/generated/questionCatalog/questions.json"),
            File("app/build/generated/questionCatalog/questions.json"),
        ).firstOrNull(File::isFile)
        requireNotNull(catalogFile) { "generated Android question catalog was not found" }

        val bank = QuestionCatalog.decode(catalogFile.readText())
        val question = bank.single { it.id == "soccer-medium-12" }
        assertEquals(TriviaCategory.SOCCER, question.category)
        assertTrue(question.prompt.contains("soccer", ignoreCase = true))
        assertEquals("Three", question.correctAnswer)
    }
}

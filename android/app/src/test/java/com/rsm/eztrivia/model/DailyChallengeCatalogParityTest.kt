package com.rsm.eztrivia.model

import com.rsm.eztrivia.data.QuestionCatalog
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyChallengeCatalogParityTest {
    private val swiftGoldenFingerprints = mapOf(
        251 to 18_180_449_488_707_544_938UL,
        252 to 8_400_231_617_344_702_755UL,
        365 to 4_864_099_897_389_026_914UL,
        512 to 4_921_592_935_424_845_763UL,
    )

    private fun bank(): List<TriviaQuestion> {
        val catalogFile = sequenceOf(
            File("build/generated/questionCatalog/questions.json"),
            File("app/build/generated/questionCatalog/questions.json"),
        ).firstOrNull(File::isFile)
        requireNotNull(catalogFile) { "generated Android question catalog was not found" }
        return QuestionCatalog.decode(catalogFile.readText())
    }

    @Test
    fun cutoverMatchesTheSharedCalendarContract() {
        assertEquals(250, DailyChallenge.day(LocalDate.of(2026, 9, 8)))
        assertEquals(251, DailyChallenge.day(LocalDate.of(2026, 9, 9)))
        assertEquals(251, DailyChallenge.CROSS_PLATFORM_START_DAY)
        assertEquals(2, DailyChallenge.ALGORITHM_VERSION)
        assertFalse(DailyChallenge.isCrossPlatformDay(250))
        assertTrue(DailyChallenge.isCrossPlatformDay(251))
        assertEquals(252, DailyChallenge.displayNumber(251))
    }

    @Test
    fun fullCatalogProducesStableTenQuestionDailyRounds() {
        val bank = bank()
        listOf(251, 252, 365, 512, 1_024).forEach { day ->
            val first = DailyChallenge.challenge(day, bank)
            val second = DailyChallenge.challenge(day, bank)

            assertEquals(DailyChallenge.QUESTION_COUNT, first.size)
            assertEquals(first.map(TriviaQuestion::id), second.map(TriviaQuestion::id))
            assertEquals(first.map(TriviaQuestion::answers), second.map(TriviaQuestion::answers))
            assertEquals(first.map(TriviaQuestion::correctAnswerIndex), second.map(TriviaQuestion::correctAnswerIndex))
            assertEquals(DailyChallenge.difficultyRamp, first.map(TriviaQuestion::difficulty))
            assertEquals(DailyChallenge.QUESTION_COUNT, first.map(TriviaQuestion::category).toSet().size)
        }
    }

    @Test
    fun flagDailyKeepsFourDistinctChoicesAndOneCorrectAnswer() {
        val bank = bank()
        val day = (DailyChallenge.CROSS_PLATFORM_START_DAY..900).first { candidate ->
            DailyChallenge.challenge(candidate, bank).any { it.category == TriviaCategory.FLAGS }
        }
        val flag = DailyChallenge.challenge(day, bank).first { it.category == TriviaCategory.FLAGS }

        assertEquals(4, flag.answers.size)
        assertEquals(4, flag.answers.toSet().size)
        assertTrue(flag.correctAnswerIndex in flag.answers.indices)
        assertEquals(flag.correctAnswer, flag.answers[flag.correctAnswerIndex])
    }

    @Test
    fun androidMatchesTheSwiftDailyV2GoldenFingerprints() {
        val bank = bank()
        swiftGoldenFingerprints.forEach { (day, expected) ->
            assertEquals(expected, fingerprint(DailyChallenge.challenge(day, bank)))
        }
    }

    private fun fingerprint(questions: List<TriviaQuestion>): ULong {
        val payload = questions.joinToString("\u001D") { question ->
            "${question.id}\u001F${question.correctAnswerIndex}\u001F${question.answers.joinToString("\u001E")}"
        }
        var hash = 14_695_981_039_346_656_037UL
        payload.encodeToByteArray().forEach { byte ->
            hash = hash xor byte.toUByte().toULong()
            hash *= 1_099_511_628_211UL
        }
        return hash
    }
}

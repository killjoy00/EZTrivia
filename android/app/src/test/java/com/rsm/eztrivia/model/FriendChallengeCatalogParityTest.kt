package com.rsm.eztrivia.model

import com.rsm.eztrivia.data.QuestionCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises Friend Challenge against the generated full catalog rather than a
 * toy bank. The Gradle test task runs after generateQuestionCatalog, so this is
 * also a guard that the Swift-authored bank order and flag parity metadata stay
 * consumable by the Android deterministic constructor.
 */
class FriendChallengeCatalogParityTest {
    @Test
    fun fullCatalogProducesStableTenQuestionChallenges() {
        val catalogFile = sequenceOf(
            File("build/generated/questionCatalog/questions.json"),
            File("app/build/generated/questionCatalog/questions.json"),
        ).firstOrNull(File::isFile)
        requireNotNull(catalogFile) { "generated Android question catalog was not found" }

        val bank = QuestionCatalog.decode(catalogFile.readText())
        val seeds = listOf(0UL, 1UL, 42UL, 0x123456789ABCDEF0UL, ULong.MAX_VALUE)

        seeds.forEach { seed ->
            val first = FriendChallenge.challenge(seed, bank)
            val second = FriendChallenge.challenge(seed, bank)
            assertEquals(FriendChallenge.QUESTION_COUNT, first.size)
            assertEquals(first.map(TriviaQuestion::id), second.map(TriviaQuestion::id))
            assertEquals(first.map(TriviaQuestion::answers), second.map(TriviaQuestion::answers))
            assertEquals(first.map(TriviaQuestion::correctAnswerIndex), second.map(TriviaQuestion::correctAnswerIndex))
            assertEquals(FriendChallenge.difficultyRamp, first.map(TriviaQuestion::difficulty))
            assertEquals(FriendChallenge.QUESTION_COUNT, first.map(TriviaQuestion::category).toSet().size)
        }
    }

    @Test
    fun fullCatalogFlagChallengesKeepOneCorrectAndFourDistinctChoices() {
        val catalogFile = sequenceOf(
            File("build/generated/questionCatalog/questions.json"),
            File("app/build/generated/questionCatalog/questions.json"),
        ).firstOrNull(File::isFile)
        requireNotNull(catalogFile) { "generated Android question catalog was not found" }
        val bank = QuestionCatalog.decode(catalogFile.readText())

        val seed = (0UL..500UL).first { candidate ->
            FriendChallenge.challenge(candidate, bank).any { it.category == TriviaCategory.FLAGS }
        }
        val flag = FriendChallenge.challenge(seed, bank).first { it.category == TriviaCategory.FLAGS }

        assertEquals(4, flag.answers.size)
        assertEquals(4, flag.answers.toSet().size)
        assertTrue(flag.correctAnswerIndex in flag.answers.indices)
        assertEquals(flag.correctAnswer, flag.answers[flag.correctAnswerIndex])
    }
}

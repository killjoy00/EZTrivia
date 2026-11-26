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
    private fun bank(): List<TriviaQuestion> {
        val catalogFile = sequenceOf(
            File("build/generated/questionCatalog/questions.json"),
            File("app/build/generated/questionCatalog/questions.json"),
        ).firstOrNull(File::isFile)
        requireNotNull(catalogFile) { "generated Android question catalog was not found" }
        return QuestionCatalog.decode(catalogFile.readText())
    }

    @Test
    fun fullCatalogProducesStableTenQuestionChallenges() {
        val bank = bank()
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
    fun bothFriendChallengeVersionsMatchSwiftCatalogFixtures() {
        val bank = bank()

        assertEquals(2_589_440_570_255_946_010UL, catalogFingerprint(FriendChallenge.challenge(1UL, bank, 3)))
        assertEquals(3_017_420_348_180_732_185UL, catalogFingerprint(FriendChallenge.challenge(1UL, bank, 4)))
        assertEquals(14_951_453_120_897_569_346UL, catalogFingerprint(FriendChallenge.challenge(42UL, bank, 3)))
        assertEquals(18_138_656_064_128_758_236UL, catalogFingerprint(FriendChallenge.challenge(42UL, bank, 4)))
    }

    @Test
    fun fullCatalogFlagChallengesKeepOneCorrectAndFourDistinctChoices() {
        val bank = bank()

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

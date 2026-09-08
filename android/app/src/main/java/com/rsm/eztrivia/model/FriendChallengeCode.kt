package com.rsm.eztrivia.model

import kotlinx.serialization.Serializable

@Serializable
data class FriendChallengeCode(
    val version: Int = CODE_VERSION,
    val seed: ULong,
    val targetScore: Int,
    val targetPoints: Int,
) {
    init {
        require(version in 1..9)
        require(targetScore in 0..FriendChallenge.QUESTION_COUNT)
        require(targetPoints in 0..FriendChallenge.MAXIMUM_POINTS)
    }

    val attemptId: String get() = "v$version-$seed"

    val displayString: String
        get() {
            val body = buildString {
                append(encode(seed, 13))
                append(encode(targetScore.toULong(), 1))
                append(encode(targetPoints.toULong(), 3))
                append(encode(checksum(seed, targetScore, targetPoints, version), 2))
            }
            return prefix(version) + "-" + body.chunked(4).joinToString("-")
        }

    sealed interface RejectionReason {
        data object Unreadable : RejectionReason
        data class UnsupportedVersion(val version: Int) : RejectionReason
    }

    companion object {
        const val CODE_VERSION = 3
        private const val BODY_LENGTH = 19
        private const val PREFIX_LENGTH = 3
        private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        private val alphabetLookup = ALPHABET.withIndex().associate { it.value to it.index.toULong() }

        fun parse(rawValue: String): FriendChallengeCode? {
            val compact = normalized(rawValue)
            if (compact.length != PREFIX_LENGTH + BODY_LENGTH) return null
            if (!compact.startsWith(prefix(CODE_VERSION))) return null

            val body = compact.drop(PREFIX_LENGTH)
            val decodedSeed = decode(body.substring(0, 13)) ?: return null
            val decodedScore = decode(body.substring(13, 14)) ?: return null
            val decodedPoints = decode(body.substring(14, 17)) ?: return null
            val decodedChecksum = decode(body.substring(17, 19)) ?: return null
            if (decodedScore > FriendChallenge.QUESTION_COUNT.toULong()) return null
            if (decodedPoints > FriendChallenge.MAXIMUM_POINTS.toULong()) return null

            val score = decodedScore.toInt()
            val points = decodedPoints.toInt()
            if (decodedChecksum != checksum(decodedSeed, score, points, CODE_VERSION)) return null

            return FriendChallengeCode(
                seed = decodedSeed,
                targetScore = score,
                targetPoints = points,
            )
        }

        fun rejectionReason(rawValue: String): RejectionReason? {
            if (parse(rawValue) != null) return null
            val compact = normalized(rawValue)
            if (compact.length != PREFIX_LENGTH + BODY_LENGTH || !compact.startsWith("EZ")) {
                return RejectionReason.Unreadable
            }
            val version = compact.getOrNull(2)?.digitToIntOrNull() ?: return RejectionReason.Unreadable
            return if (version != CODE_VERSION) {
                RejectionReason.UnsupportedVersion(version)
            } else {
                RejectionReason.Unreadable
            }
        }

        private fun prefix(version: Int): String = "EZ$version"

        private fun normalized(rawValue: String): String = buildString {
            rawValue.uppercase().forEach { character ->
                if (character.isLetterOrDigit()) {
                    append(
                        when (character) {
                            'O' -> '0'
                            'I', 'L' -> '1'
                            else -> character
                        }
                    )
                }
            }
        }

        private fun encode(value: ULong, width: Int): String {
            var remaining = value
            val characters = CharArray(width) { '0' }
            for (index in characters.indices.reversed()) {
                characters[index] = ALPHABET[(remaining and 31UL).toInt()]
                remaining = remaining shr 5
            }
            return characters.concatToString()
        }

        private fun decode(characters: String): ULong? {
            var value = 0UL
            for (character in characters) {
                val digit = alphabetLookup[character] ?: return null
                if (value > (ULong.MAX_VALUE - digit) / 32UL) return null
                value = value * 32UL + digit
            }
            return value
        }

        private fun checksum(seed: ULong, score: Int, points: Int, version: Int): ULong {
            var value = seed
            value = value xor (score.toULong() * 0x9E3779B97F4A7C15UL)
            value = value xor (points.toULong() * 0xBF58476D1CE4E5B9UL)
            value = value xor (version.toULong() * 0x94D049BB133111EBUL)
            value = value xor (value shr 30)
            value *= 0xBF58476D1CE4E5B9UL
            value = value xor (value shr 27)
            value *= 0x94D049BB133111EBUL
            value = value xor (value shr 31)
            return value and 0x3FFUL
        }
    }
}

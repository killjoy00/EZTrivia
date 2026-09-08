package com.rsm.eztrivia.model

/** Mirrors EZTriviaCore.SeededGenerator exactly for cross-platform challenge work. */
class SeededGenerator(seed: ULong) {
    private var state: ULong = if (seed == 0UL) 0x9E3779B97F4A7C15UL else seed

    fun nextULong(): ULong {
        state = state xor (state shl 13)
        state = state xor (state shr 7)
        state = state xor (state shl 17)
        return state
    }
}

fun <T> List<T>.deterministicallyShuffled(generator: SeededGenerator): List<T> {
    val result = toMutableList()
    if (result.size <= 1) return result

    for (index in result.lastIndex downTo 1) {
        val other = (generator.nextULong() % (index + 1).toULong()).toInt()
        if (other != index) {
            val value = result[index]
            result[index] = result[other]
            result[other] = value
        }
    }
    return result
}

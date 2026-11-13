package io.github.camilus123.game4096.core

private val GAMMA = 0x9E3779B97F4A7C15uL.toLong()
private val MIX1 = 0xBF58476D1CE4E5B9uL.toLong()
private val MIX2 = 0x94D049BB133111EBuL.toLong()

/**
 * Small deterministic generator (SplitMix64).
 *
 * The whole generator is one [state] value, so a game can be saved, restored and
 * replayed exactly, and tests can pin a seed. Never allocates.
 */
class SeededRandom(var state: Long) {

    fun nextLong(): Long {
        state += GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX1
        z = (z xor (z ushr 27)) * MIX2
        return z xor (z ushr 31)
    }

    /** Uniform in `0 until bound`. [bound] must be positive. */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        // Top 31 bits; modulo bias is below 2^-26 for the tiny bounds used here.
        return ((nextLong() ushr 33) % bound).toInt()
    }
}

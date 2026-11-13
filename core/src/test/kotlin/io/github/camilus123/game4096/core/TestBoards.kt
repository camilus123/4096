package io.github.camilus123.game4096.core

/** Builds an exponent board from face values: `board(2, 0, 4, 4)`. */
fun board(vararg values: Int): IntArray = IntArray(values.size) { i ->
    val v = values[i]
    if (v == 0) 0 else {
        require(v > 1 && v and (v - 1) == 0) { "$v is not a tile value" }
        Integer.numberOfTrailingZeros(v)
    }
}

fun IntArray.values(): List<Long> = map { Rules.tileValue(it) }

fun IntArray.sumOfValues(): Long = sumOf { Rules.tileValue(it) }

val ALL_SIZES = intArrayOf(4, 5, 6)

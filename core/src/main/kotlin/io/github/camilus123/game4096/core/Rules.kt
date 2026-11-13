package io.github.camilus123.game4096.core

enum class Direction { LEFT, RIGHT, UP, DOWN }

/**
 * Where every tile went during one move, for the renderer to animate.
 * Reused between moves so the move path never allocates.
 */
class MoveTrace(cellCount: Int) {
    /** For each source cell: the cell its tile ended in, or -1 if the source was empty. */
    val dest = IntArray(cellCount) { -1 }

    /** For each destination cell: true if two tiles merged there this move. */
    val merged = BooleanArray(cellCount)

    fun clear() {
        dest.fill(-1)
        merged.fill(false)
    }
}

/**
 * The rules of 2048 as pure functions over a flat board.
 *
 * A board is an IntArray of `size * size` cells in row-major order. Each cell holds
 * the tile's exponent: 0 is empty, 1 is the "2" tile, 2 is "4", 11 is "2048".
 * Exponents keep big boards safe from overflow (a 6x6 board can exceed 2^31).
 *
 * Nothing here allocates, so the opponent's search can call it millions of times.
 */
object Rules {

    const val MIN_SIZE = 2
    const val MAX_SIZE = 8

    /** Returned by [move] when the swipe changes nothing (and so is not a legal move). */
    const val NO_MOVE = -1L

    fun tileValue(exponent: Int): Long = if (exponent == 0) 0L else 1L shl exponent

    /**
     * Slides [src] in [dir] and writes the result to [dst] ([dst] may be the same array).
     *
     * Each tile merges at most once per move, and merges resolve from the edge the
     * tiles slide towards: sliding `2 2 2` left gives `4 2`, right gives `2 4`.
     *
     * @return the score gained (the sum of the values of the tiles created by merging),
     *   or [NO_MOVE] if no tile moved.
     */
    fun move(src: IntArray, size: Int, dir: Direction, dst: IntArray, trace: MoveTrace? = null): Long {
        trace?.clear()
        var gain = 0L
        var changed = false
        for (line in 0 until size) {
            val start: Int
            val step: Int
            when (dir) {
                Direction.LEFT -> { start = line * size; step = 1 }
                Direction.RIGHT -> { start = line * size + size - 1; step = -1 }
                Direction.UP -> { start = line; step = size }
                Direction.DOWN -> { start = (size - 1) * size + line; step = -size }
            }
            var write = 0
            var mergeable = 0 // exponent of the last placed tile if it may still merge
            for (i in 0 until size) {
                val from = start + i * step
                val e = src[from]
                if (e == 0) continue
                if (e == mergeable) {
                    val to = start + (write - 1) * step
                    dst[to] = e + 1
                    gain += 1L shl (e + 1)
                    mergeable = 0
                    changed = true
                    if (trace != null) {
                        trace.dest[from] = to
                        trace.merged[to] = true
                    }
                } else {
                    val to = start + write * step
                    dst[to] = e
                    mergeable = e
                    if (to != from) changed = true
                    if (trace != null) trace.dest[from] = to
                    write++
                }
            }
            for (i in write until size) dst[start + i * step] = 0
        }
        return if (changed) gain else NO_MOVE
    }

    /** True if a swipe in [dir] would change the board. Does not modify anything. */
    fun canMove(cells: IntArray, size: Int, dir: Direction): Boolean {
        for (line in 0 until size) {
            val start: Int
            val step: Int
            when (dir) {
                Direction.LEFT -> { start = line * size; step = 1 }
                Direction.RIGHT -> { start = line * size + size - 1; step = -1 }
                Direction.UP -> { start = line; step = size }
                Direction.DOWN -> { start = (size - 1) * size + line; step = -size }
            }
            var prev = cells[start]
            for (i in 1 until size) {
                val e = cells[start + i * step]
                if (e != 0 && (prev == 0 || prev == e)) return true
                prev = e
            }
        }
        return false
    }

    /** True if at least one swipe is still possible. */
    fun hasMoves(cells: IntArray, size: Int): Boolean {
        for (r in 0 until size) {
            for (c in 0 until size) {
                val e = cells[r * size + c]
                if (e == 0) return true
                if (c + 1 < size && cells[r * size + c + 1] == e) return true
                if (r + 1 < size && cells[(r + 1) * size + c] == e) return true
            }
        }
        return false
    }

    fun countEmpty(cells: IntArray): Int {
        var n = 0
        for (e in cells) if (e == 0) n++
        return n
    }

    /** As [countEmpty], over the first [count] cells of a larger buffer. */
    fun countEmpty(cells: IntArray, count: Int): Int {
        var n = 0
        for (i in 0 until count) if (cells[i] == 0) n++
        return n
    }

    /** As [maxExponent], over the first [count] cells of a larger buffer. */
    fun maxExponent(cells: IntArray, count: Int): Int {
        var m = 0
        for (i in 0 until count) if (cells[i] > m) m = cells[i]
        return m
    }

    fun maxExponent(cells: IntArray): Int {
        var m = 0
        for (e in cells) if (e > m) m = e
        return m
    }

    /**
     * Puts a new tile on a uniformly random empty cell: a 2 with 90% chance, a 4 with 10%.
     * @return the index of the new tile, or -1 if the board was full.
     */
    fun spawn(cells: IntArray, rng: SeededRandom): Int {
        val empty = countEmpty(cells)
        if (empty == 0) return -1
        var pick = rng.nextInt(empty)
        val exponent = if (rng.nextInt(10) == 0) 2 else 1
        for (i in cells.indices) {
            if (cells[i] == 0) {
                if (pick == 0) {
                    cells[i] = exponent
                    return i
                }
                pick--
            }
        }
        return -1
    }

    /** The tile that counts as "winning" on each grid: 2048 on 4x4, 4096 on 5x5, 8192 on 6x6. */
    fun defaultWinExponent(size: Int): Int = when {
        size <= 4 -> 11
        size == 5 -> 12
        else -> 13
    }
}

package io.github.camilus123.game4096.core

enum class Difficulty(val id: String) {
    EASY("easy"),
    MEDIUM("medium"),
    HARD("hard");

    companion object {
        fun parse(id: String?): Difficulty? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The computer opponent. It plays by the same [Rules] as the player and only ever sees a
 * board; it has no access to upcoming tiles.
 *
 *  - EASY looks one move ahead with a naive taste for merging and makes frequent random
 *    moves, about as well as someone playing casually.
 *  - MEDIUM looks one move ahead with a good positional sense and rarely slips.
 *  - HARD runs an expectimax search: it weighs each of its moves against every tile that
 *    could appear next, several moves deep.
 *
 * One instance must be used from one thread at a time. After construction nothing allocates.
 */
class Bot(val difficulty: Difficulty, seed: Long) {

    private val rng = SeededRandom(seed)
    private val buffers = Array(MAX_PLIES + 1) { IntArray(Rules.MAX_SIZE * Rules.MAX_SIZE) }
    private val root = IntArray(Rules.MAX_SIZE * Rules.MAX_SIZE)
    private var weights = DoubleArray(0)
    private var weightsSize = 0
    private var size = 0
    private var cellCount = 0

    /** How long the opponent "takes" over its next move, in milliseconds. Faster when stronger. */
    fun nextDelayMs(): Long = when (difficulty) {
        Difficulty.EASY -> 650L + rng.nextInt(500)
        Difficulty.MEDIUM -> 450L + rng.nextInt(300)
        Difficulty.HARD -> 300L + rng.nextInt(180)
    }

    /** Picks a move for [cells] (which is not modified), or null if no move is possible. */
    fun chooseMove(cells: IntArray, boardSize: Int): Direction? {
        size = boardSize
        cellCount = boardSize * boardSize
        if (weightsSize != boardSize) {
            weights = snakeWeights(boardSize)
            weightsSize = boardSize
        }
        System.arraycopy(cells, 0, root, 0, cellCount)

        val slipChance = when (difficulty) {
            Difficulty.EASY -> EASY_SLIP_PERCENT
            Difficulty.MEDIUM -> MEDIUM_SLIP_PERCENT
            Difficulty.HARD -> 0
        }
        if (slipChance > 0 && rng.nextInt(100) < slipChance) return randomMove()

        var best: Direction? = null
        var bestValue = Double.NEGATIVE_INFINITY
        val plies = when (difficulty) {
            Difficulty.EASY -> 1
            Difficulty.MEDIUM -> 2
            Difficulty.HARD -> hardPlies()
        }
        val next = buffers[plies]
        for (dir in DIRECTIONS) {
            val gain = Rules.move(root, size, dir, next)
            if (gain == Rules.NO_MOVE) continue
            val value = when (difficulty) {
                Difficulty.EASY -> gain + 2.0 * Rules.countEmpty(next, cellCount) + rng.nextInt(3)
                else -> gain + chanceValue(next, plies - 1)
            }
            if (value > bestValue) {
                bestValue = value
                best = dir
            }
        }
        return best
    }

    private fun randomMove(): Direction? {
        val first = rng.nextInt(4)
        for (k in 0 until 4) {
            val dir = DIRECTIONS[(first + k) % 4]
            if (Rules.canMove(root, size, dir)) return dir
        }
        return null
    }

    /** Search deeper when the board is crowded: that is when it matters and when it is cheap. */
    private fun hardPlies(): Int {
        val empty = Rules.countEmpty(root, cellCount)
        return when {
            empty <= 3 -> 4
            empty <= 7 -> 3
            else -> 2
        }
    }

    /** Average value of [board] over the tiles that may appear on it; [plies] more moves follow. */
    private fun chanceValue(board: IntArray, plies: Int): Double {
        if (plies == 0) return evaluate(board)
        val empty = Rules.countEmpty(board, cellCount)
        if (empty == 0) return bestMoveValue(board, plies)
        // On open boards look at a spread of cells and at 2s only; that keeps the search bounded
        // on 6x6 without changing what it concludes.
        val stride = if (empty > MAX_SPAWN_CELLS) (empty + MAX_SPAWN_CELLS - 1) / MAX_SPAWN_CELLS else 1
        val withFours = empty <= 5
        var total = 0.0
        var weight = 0.0
        var seen = 0
        for (i in 0 until cellCount) {
            if (board[i] != 0) continue
            if (seen++ % stride != 0) continue
            board[i] = 1
            total += (if (withFours) 0.9 else 1.0) * bestMoveValue(board, plies)
            weight += if (withFours) 0.9 else 1.0
            if (withFours) {
                board[i] = 2
                total += 0.1 * bestMoveValue(board, plies)
                weight += 0.1
            }
            board[i] = 0
        }
        return total / weight
    }

    /** Value of [board] when the bot is to move and plays its best move. */
    private fun bestMoveValue(board: IntArray, plies: Int): Double {
        val next = buffers[plies - 1]
        var best = Double.NEGATIVE_INFINITY
        for (dir in DIRECTIONS) {
            val gain = Rules.move(board, size, dir, next)
            if (gain == Rules.NO_MOVE) continue
            val value = gain + chanceValue(next, plies - 1)
            if (value > best) best = value
        }
        // No move at all: the game would be lost here.
        return if (best == Double.NEGATIVE_INFINITY) evaluate(board) - DEAD_PENALTY * VALUES[Rules.maxExponent(board, cellCount)] else best
    }

    /**
     * How good a board is to hold: big tiles lined up along a snake from one corner, room to
     * move, and neighbours close in value so they can merge.
     */
    private fun evaluate(board: IntArray): Double {
        var positional = 0.0
        var empty = 0
        var rough = 0.0
        var max = 0
        for (i in 0 until cellCount) {
            val e = board[i]
            if (e == 0) {
                empty++
                continue
            }
            if (e > max) max = e
            positional += VALUES[e] * weights[i]
            val col = i % size
            if (col + 1 < size) {
                val r = board[i + 1]
                if (r != 0) rough += kotlin.math.abs(VALUES[e] - VALUES[r])
            }
            if (i + size < cellCount) {
                val d = board[i + size]
                if (d != 0) rough += kotlin.math.abs(VALUES[e] - VALUES[d])
            }
        }
        return positional + EMPTY_WEIGHT * empty * VALUES[max] - ROUGH_WEIGHT * rough
    }

    private companion object {
        const val MAX_PLIES = 4
        const val MAX_SPAWN_CELLS = 6
        const val EASY_SLIP_PERCENT = 30
        const val MEDIUM_SLIP_PERCENT = 12
        const val EMPTY_WEIGHT = 0.35
        const val ROUGH_WEIGHT = 0.12
        const val DEAD_PENALTY = 40.0

        val DIRECTIONS = Direction.entries.toTypedArray()
        val VALUES = DoubleArray(64) { if (it == 0) 0.0 else Math.pow(2.0, it.toDouble()) }

        /** Weights falling by a constant ratio along a snake: across the top row, back along the next. */
        fun snakeWeights(size: Int): DoubleArray {
            val w = DoubleArray(size * size)
            var value = 16.0
            for (row in 0 until size) {
                for (k in 0 until size) {
                    val col = if (row % 2 == 0) k else size - 1 - k
                    w[row * size + col] = value
                    value *= 0.62
                }
            }
            return w
        }
    }
}

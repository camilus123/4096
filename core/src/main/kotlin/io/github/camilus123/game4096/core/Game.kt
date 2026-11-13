package io.github.camilus123.game4096.core

/** A complete, immutable picture of a game at one moment. Used for undo and for saving. */
class GameState(
    val size: Int,
    val cells: IntArray,
    val score: Long,
    val moves: Int,
    val rngState: Long,
) {
    init {
        require(cells.size == size * size) { "cells must hold size*size entries" }
    }
}

/**
 * One board being played: the board, score, move count, random generator and undo history.
 *
 * Mode-specific limits (clock, move budget, versus rules) live a layer above this class;
 * every mode and the opponent AI share this one implementation, which is what keeps
 * Versus fair.
 *
 * @param undoLimit how many moves can be taken back: 0 disables undo,
 *   [UNLIMITED_UNDO] keeps the whole history.
 */
class Game private constructor(
    val size: Int,
    val winExponent: Int,
    val undoLimit: Int,
    private val rng: SeededRandom,
) {
    private val cells = IntArray(size * size)
    private val scratch = IntArray(size * size)
    private val history = ArrayDeque<GameState>()

    var score: Long = 0L
        private set

    /** Number of moves made that changed the board. */
    var moves: Int = 0
        private set

    /** Tile movements of the most recent successful [move]. */
    val lastTrace = MoveTrace(size * size)

    /** Cell that received the new tile after the most recent successful [move], or -1. */
    var lastSpawnIndex: Int = -1
        private set

    val cellCount: Int get() = cells.size

    /** Exponent at [index] (row-major): 0 is empty, 1 is the "2" tile. */
    fun exponentAt(index: Int): Int = cells[index]

    fun exponentAt(row: Int, col: Int): Int = cells[row * size + col]

    fun valueAt(row: Int, col: Int): Long = Rules.tileValue(cells[row * size + col])

    fun copyCellsInto(target: IntArray) {
        cells.copyInto(target)
    }

    val maxExponent: Int get() = Rules.maxExponent(cells)

    /** True once a tile of at least the win value is on the board. */
    val hasWon: Boolean get() = maxExponent >= winExponent

    /** True when no swipe can change the board. */
    val isOver: Boolean get() = !Rules.hasMoves(cells, size)

    val canUndo: Boolean get() = history.isNotEmpty()

    val undoDepth: Int get() = history.size

    fun canMove(dir: Direction): Boolean = Rules.canMove(cells, size, dir)

    /**
     * Swipes the board. If anything moved, the score and move count are updated and one
     * new tile is spawned. A swipe that changes nothing is ignored and spawns nothing.
     *
     * @return the score gained by this move, or [Rules.NO_MOVE] if the swipe did nothing.
     */
    fun move(dir: Direction): Long {
        val gain = Rules.move(cells, size, dir, scratch, lastTrace)
        if (gain == Rules.NO_MOVE) return Rules.NO_MOVE
        if (undoLimit > 0) {
            if (history.size == undoLimit) history.removeFirst()
            history.addLast(snapshot())
        }
        scratch.copyInto(cells)
        score += gain
        moves++
        lastSpawnIndex = Rules.spawn(cells, rng)
        return gain
    }

    /**
     * Takes back the last move, including the tile it spawned and the random generator's
     * state, so repeating the same swipe gives the same new tile (no re-rolling by undo).
     */
    fun undo(): Boolean {
        val previous = history.removeLastOrNull() ?: return false
        load(previous)
        return true
    }

    fun snapshot(): GameState = GameState(size, cells.copyOf(), score, moves, rng.state)

    /** Undo history, oldest first. For saving. */
    fun historySnapshot(): List<GameState> = history.toList()

    private fun load(state: GameState) {
        state.cells.copyInto(cells)
        score = state.score
        moves = state.moves
        rng.state = state.rngState
        lastTrace.clear()
        lastSpawnIndex = -1
    }

    companion object {
        const val UNLIMITED_UNDO = Int.MAX_VALUE

        /** Starts a new game with two tiles on the board. The same [seed] gives the same game. */
        fun new(
            size: Int,
            seed: Long,
            winExponent: Int = Rules.defaultWinExponent(size),
            undoLimit: Int = 0,
        ): Game {
            checkConfig(size, winExponent, undoLimit)
            val game = Game(size, winExponent, undoLimit, SeededRandom(seed))
            Rules.spawn(game.cells, game.rng)
            Rules.spawn(game.cells, game.rng)
            return game
        }

        /** Rebuilds a game from saved state. [history] is the undo history, oldest first. */
        fun restore(
            state: GameState,
            winExponent: Int = Rules.defaultWinExponent(state.size),
            undoLimit: Int = 0,
            history: List<GameState> = emptyList(),
        ): Game {
            checkConfig(state.size, winExponent, undoLimit)
            val game = Game(state.size, winExponent, undoLimit, SeededRandom(state.rngState))
            game.load(state)
            if (undoLimit > 0) {
                for (h in history.takeLast(undoLimit)) {
                    require(h.size == state.size) { "history entry has a different grid size" }
                    game.history.addLast(h)
                }
            }
            return game
        }

        private fun checkConfig(size: Int, winExponent: Int, undoLimit: Int) {
            require(size in Rules.MIN_SIZE..Rules.MAX_SIZE) { "unsupported grid size $size" }
            require(winExponent in 2..62) { "unsupported win exponent $winExponent" }
            require(undoLimit >= 0) { "undoLimit must not be negative" }
        }
    }
}

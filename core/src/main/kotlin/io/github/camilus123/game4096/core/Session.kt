package io.github.camilus123.game4096.core

enum class Mode(val id: String) {
    CLASSIC("classic"),
    ZEN("zen"),
    TIME_ATTACK("time"),
    LIMITED_MOVES("moves"),

    /** Against the computer. Played through [VersusMatch], not [Session]. */
    VERSUS("versus"),
}

/**
 * A mode plus its variant: the number of seconds in Time attack, the move budget in
 * Limited moves, 0 for modes without variants. [id] names the save slot and the score table,
 * so each duration and each budget has its own.
 */
class GameRules(val mode: Mode, val variant: Int = 0) {

    init {
        when (mode) {
            Mode.TIME_ATTACK, Mode.LIMITED_MOVES -> require(variant > 0) { "$mode needs a positive variant" }
            Mode.VERSUS -> throw IllegalArgumentException("Versus is configured by VersusConfig")
            else -> require(variant == 0) { "$mode has no variants" }
        }
    }

    val id: String get() = if (variant == 0) mode.id else "${mode.id}_$variant"

    val isTimed: Boolean get() = mode == Mode.TIME_ATTACK
    val timeLimitMs: Long get() = if (isTimed) variant * 1000L else 0L
    val moveBudget: Int get() = if (mode == Mode.LIMITED_MOVES) variant else 0
    val undoLimit: Int get() = if (mode == Mode.ZEN) Game.UNLIMITED_UNDO else 0

    /** Whether reaching the win tile interrupts play. It does not where the score is the goal. */
    val announcesWin: Boolean get() = mode == Mode.CLASSIC || mode == Mode.ZEN

    override fun equals(other: Any?): Boolean = other is GameRules && other.mode == mode && other.variant == variant
    override fun hashCode(): Int = mode.ordinal * 100_003 + variant
    override fun toString(): String = id

    companion object {
        val CLASSIC = GameRules(Mode.CLASSIC)
        val ZEN = GameRules(Mode.ZEN)

        /** Seconds on the clock offered in Time attack. */
        val TIME_VARIANTS = intArrayOf(60, 180, 300)

        /** Move budgets offered in Limited moves. */
        val MOVE_VARIANTS = intArrayOf(50, 100, 200)

        /** Every rule set that has its own score table, in display order. */
        val ALL: List<GameRules> = buildList {
            add(CLASSIC)
            add(ZEN)
            for (v in TIME_VARIANTS) add(GameRules(Mode.TIME_ATTACK, v))
            for (v in MOVE_VARIANTS) add(GameRules(Mode.LIMITED_MOVES, v))
        }

        fun parse(id: String?): GameRules? {
            if (id == null) return null
            val cut = id.indexOf('_')
            val modeId = if (cut < 0) id else id.substring(0, cut)
            val mode = Mode.entries.firstOrNull { it.id == modeId } ?: return null
            val variant = if (cut < 0) 0 else id.substring(cut + 1).toIntOrNull() ?: return null
            return try {
                GameRules(mode, variant)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}

enum class SessionStatus {
    PLAYING,

    /** The win tile is on the board and the player has not yet chosen to keep playing. */
    WON,

    /** Zen only: no swipe is possible, but the player can undo. */
    STUCK,

    OVER,
}

enum class EndReason { NONE, NO_MOVES, TIME_UP, OUT_OF_MOVES }

/**
 * One game being played under a set of rules: the board plus the clock, the move budget and
 * the win/over logic that differ between modes. Pure Kotlin; the app feeds it elapsed time.
 */
class Session(
    val rules: GameRules,
    val game: Game,
    timeLeftMs: Long = rules.timeLimitMs,
    var winAcknowledged: Boolean = false,
) {
    /** Milliseconds left in Time attack. Meaningless in other modes. */
    var timeLeftMs: Long = if (rules.isTimed) timeLeftMs.coerceIn(0L, rules.timeLimitMs) else 0L
        private set

    /** Moves left in Limited moves, or -1 in other modes. */
    val movesLeft: Int
        get() = if (rules.moveBudget > 0) maxOf(0, rules.moveBudget - game.moves) else -1

    val endReason: EndReason
        get() = when {
            rules.isTimed && timeLeftMs <= 0L -> EndReason.TIME_UP
            rules.moveBudget > 0 && game.moves >= rules.moveBudget -> EndReason.OUT_OF_MOVES
            game.isOver -> EndReason.NO_MOVES
            else -> EndReason.NONE
        }

    val status: SessionStatus
        get() = when (endReason) {
            EndReason.NONE ->
                if (rules.announcesWin && game.hasWon && !winAcknowledged) SessionStatus.WON else SessionStatus.PLAYING
            EndReason.NO_MOVES ->
                if (rules.mode == Mode.ZEN && game.canUndo) SessionStatus.STUCK else SessionStatus.OVER
            else -> SessionStatus.OVER
        }

    /** True while swipes should be accepted. */
    val acceptsMoves: Boolean get() = status == SessionStatus.PLAYING

    /** The clock starts with the first move, so looking at a fresh board costs no time. */
    val clockRunning: Boolean
        get() = rules.isTimed && game.moves > 0 && status == SessionStatus.PLAYING

    /** Takes [elapsedMs] off the clock if it is running. */
    fun advanceClock(elapsedMs: Long) {
        if (elapsedMs > 0L && clockRunning) timeLeftMs = maxOf(0L, timeLeftMs - elapsedMs)
    }

    fun undo(): Boolean = rules.undoLimit > 0 && game.undo()

    fun toSavedGame(maxHistory: Int = 200): SavedGame =
        SavedGame(game.snapshot(), game.historySnapshot().takeLast(maxHistory), winAcknowledged, timeLeftMs)

    companion object {
        fun new(rules: GameRules, size: Int, seed: Long): Session =
            Session(rules, Game.new(size, seed, Rules.defaultWinExponent(size), rules.undoLimit))

        fun restore(rules: GameRules, saved: SavedGame): Session {
            val size = saved.state.size
            val game = Game.restore(saved.state, Rules.defaultWinExponent(size), rules.undoLimit, saved.history)
            return Session(rules, game, saved.extra, saved.winAcknowledged)
        }
    }
}

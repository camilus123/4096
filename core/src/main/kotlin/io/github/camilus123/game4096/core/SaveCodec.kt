package io.github.camilus123.game4096.core

/** Everything needed to put an unfinished game back exactly as it was. */
class SavedGame(
    val state: GameState,
    /** Undo history, oldest first. Empty for modes without undo. */
    val history: List<GameState>,
    /** True if the player already saw the "you win" screen and chose to keep playing. */
    val winAcknowledged: Boolean,
)

/**
 * Text format for saved games. Plain text keeps saves readable in a bug report and easy to
 * version. [decode] never throws: anything it cannot fully understand is reported as null,
 * so a damaged save starts a new game instead of crashing the app.
 *
 * Layout: `1;<size>;<ack 0|1>;<state>;<history state>;...` where a state is
 * `<score>,<moves>,<rngState>,<cell> <cell> ...`.
 */
object SaveCodec {

    private const val VERSION = "1"

    /** Only the newest [maxHistory] undo steps are kept, to bound the size of a save. */
    fun encode(game: Game, winAcknowledged: Boolean, maxHistory: Int = 200): String =
        encode(SavedGame(game.snapshot(), game.historySnapshot().takeLast(maxHistory), winAcknowledged))

    fun encode(saved: SavedGame): String {
        val sb = StringBuilder(64 + (saved.history.size + 1) * saved.state.cells.size * 3)
        sb.append(VERSION).append(';').append(saved.state.size).append(';')
        sb.append(if (saved.winAcknowledged) '1' else '0')
        appendState(sb, saved.state)
        for (h in saved.history) appendState(sb, h)
        return sb.toString()
    }

    private fun appendState(sb: StringBuilder, s: GameState) {
        sb.append(';').append(s.score).append(',').append(s.moves).append(',').append(s.rngState).append(',')
        for (i in s.cells.indices) {
            if (i > 0) sb.append(' ')
            sb.append(s.cells[i])
        }
    }

    fun decode(text: String?): SavedGame? {
        if (text.isNullOrEmpty()) return null
        return try {
            val parts = text.split(';')
            if (parts.size < 4 || parts[0] != VERSION) return null
            val size = parts[1].toInt()
            if (size !in Rules.MIN_SIZE..Rules.MAX_SIZE) return null
            val ack = when (parts[2]) {
                "1" -> true
                "0" -> false
                else -> return null
            }
            val state = decodeState(parts[3], size) ?: return null
            val history = ArrayList<GameState>(parts.size - 4)
            for (i in 4 until parts.size) history.add(decodeState(parts[i], size) ?: return null)
            SavedGame(state, history, ack)
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun decodeState(text: String, size: Int): GameState? {
        val fields = text.split(',')
        if (fields.size != 4) return null
        val score = fields[0].toLong()
        val moves = fields[1].toInt()
        val rng = fields[2].toLong()
        val cellTexts = fields[3].split(' ')
        if (cellTexts.size != size * size || score < 0 || moves < 0) return null
        val cells = IntArray(cellTexts.size)
        for (i in cells.indices) {
            val e = cellTexts[i].toInt()
            if (e !in 0..62) return null
            cells[i] = e
        }
        return GameState(size, cells, score, moves, rng)
    }

    /** Rebuilds a playable game from a save. */
    fun restore(saved: SavedGame, winExponent: Int, undoLimit: Int): Game =
        Game.restore(saved.state, winExponent, undoLimit, saved.history)
}

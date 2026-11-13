package io.github.camilus123.game4096.data

import android.content.Context
import io.github.camilus123.game4096.core.Game
import io.github.camilus123.game4096.core.SaveCodec
import io.github.camilus123.game4096.core.SavedGame
import io.github.camilus123.game4096.core.ScoreEntry
import io.github.camilus123.game4096.core.ScoreTable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Identifies one save slot and one high-score table: a grid size plus a mode. */
data class GameKey(val size: Int, val modeId: String) {
    val id: String get() = "${modeId}_$size"

    companion object {
        const val CLASSIC = "classic"

        fun parse(id: String?): GameKey? {
            if (id == null) return null
            val cut = id.lastIndexOf('_')
            if (cut <= 0) return null
            val size = id.substring(cut + 1).toIntOrNull() ?: return null
            return GameKey(size, id.substring(0, cut))
        }
    }
}

/**
 * Saved games, high scores and small session facts, kept in the app's private storage.
 *
 * Reads are answered from memory. Writes update memory at once and go to disk on one
 * background thread, in order, so the main thread never waits for the disk during play.
 */
class Storage(context: Context) {

    private val appContext = context.applicationContext
    private val saves = appContext.getSharedPreferences("saves", Context.MODE_PRIVATE)
    private val scores = appContext.getSharedPreferences("scores", Context.MODE_PRIVATE)
    private val session = appContext.getSharedPreferences("session", Context.MODE_PRIVATE)

    private val io: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "storage-io").apply { priority = Thread.MIN_PRIORITY + 1 }
    }

    private val saveCache = HashMap<String, SavedGame?>()
    private val scoreCache = HashMap<String, ScoreTable>()

    // ---- Saved games -------------------------------------------------------------------

    fun loadGame(key: GameKey): SavedGame? {
        val id = key.id
        if (!saveCache.containsKey(id)) {
            val decoded = SaveCodec.decode(saves.getString(id, null))
            saveCache[id] = if (decoded != null && decoded.state.size == key.size) decoded else null
        }
        return saveCache[id]
    }

    fun hasGame(key: GameKey): Boolean = loadGame(key) != null

    fun saveGame(key: GameKey, game: Game, winAcknowledged: Boolean) {
        val saved = SavedGame(game.snapshot(), game.historySnapshot().takeLast(MAX_SAVED_UNDO), winAcknowledged)
        val id = key.id
        saveCache[id] = saved
        io.execute { saves.edit().putString(id, SaveCodec.encode(saved)).commit() }
    }

    fun clearGame(key: GameKey) {
        val id = key.id
        saveCache[id] = null
        io.execute { saves.edit().remove(id).commit() }
    }

    // ---- High scores -------------------------------------------------------------------

    fun scores(key: GameKey): ScoreTable =
        scoreCache.getOrPut(key.id) { ScoreTable.decode(scores.getString(key.id, null)) }

    /** @return the rank the entry took (0 is first), or -1 if it did not make the table. */
    fun addScore(key: GameKey, entry: ScoreEntry): Int {
        val table = scores(key)
        val rank = table.rankFor(entry.score)
        if (rank < 0) return -1
        val updated = table.plus(entry)
        val id = key.id
        scoreCache[id] = updated
        io.execute { scores.edit().putString(id, updated.encode()).commit() }
        return rank
    }

    // ---- Session -----------------------------------------------------------------------

    /** The game that was on screen when the app was last used, if any. */
    var activeGame: GameKey?
        get() = GameKey.parse(session.getString(KEY_ACTIVE, null))
        set(value) {
            session.edit().putString(KEY_ACTIVE, value?.id).apply()
        }

    var lastGridSize: Int
        get() = session.getInt(KEY_GRID, 4)
        set(value) {
            session.edit().putInt(KEY_GRID, value).apply()
        }

    /** Blocks until every queued write is on disk. For tests and for leaving the app. */
    fun flush() {
        io.submit {}.get(5, TimeUnit.SECONDS)
    }

    companion object {
        private const val KEY_ACTIVE = "active_game"
        private const val KEY_GRID = "grid_size"
        private const val MAX_SAVED_UNDO = 200

        @Volatile private var instance: Storage? = null

        /** Forgets the shared instance, so a test starts from what is on disk. */
        internal fun resetForTests() {
            instance = null
        }

        fun get(context: Context): Storage =
            instance ?: synchronized(this) {
                instance ?: Storage(context).also { instance = it }
            }
    }
}

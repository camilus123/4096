package io.github.camilus123.game4096.data

import android.content.Context
import io.github.camilus123.game4096.core.Difficulty
import io.github.camilus123.game4096.core.Outcome
import io.github.camilus123.game4096.core.SaveCodec
import io.github.camilus123.game4096.core.VersusMatch
import io.github.camilus123.game4096.core.VersusRecord
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
        const val VERSUS = "versus"

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

    fun saveGame(key: GameKey, saved: SavedGame) {
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

    // ---- Versus AI -------------------------------------------------------------------

    private val versus = appContext.getSharedPreferences("versus", Context.MODE_PRIVATE)
    private val versusSaveCache = HashMap<Int, String?>()
    private val versusRecordCache = HashMap<String, VersusRecord>()

    /** The unfinished match on this grid, if there is one. Each call builds a fresh copy. */
    fun loadVersus(size: Int): VersusMatch? {
        if (!versusSaveCache.containsKey(size)) versusSaveCache[size] = saves.getString("versus_$size", null)
        val match = VersusMatch.decode(versusSaveCache[size]) ?: return null
        return if (match.player.size == size) match else null
    }

    fun saveVersus(size: Int, encoded: String) {
        versusSaveCache[size] = encoded
        io.execute { saves.edit().putString("versus_$size", encoded).commit() }
    }

    fun clearVersus(size: Int) {
        versusSaveCache[size] = null
        io.execute { saves.edit().remove("versus_$size").commit() }
    }

    fun versusRecord(size: Int, difficulty: Difficulty): VersusRecord {
        val id = "${size}_${difficulty.id}"
        return versusRecordCache.getOrPut(id) { VersusRecord.decode(versus.getString(id, null)) }
    }

    fun addVersusResult(size: Int, difficulty: Difficulty, outcome: Outcome): VersusRecord {
        val id = "${size}_${difficulty.id}"
        val updated = versusRecord(size, difficulty).plus(outcome)
        versusRecordCache[id] = updated
        io.execute { versus.edit().putString(id, updated.encode()).commit() }
        return updated
    }

    /** Id of the Versus setup last chosen in the menu (see VersusConfig.id). */
    var lastVersusId: String?
        get() = session.getString(KEY_VERSUS, null)
        set(value) {
            session.edit().putString(KEY_VERSUS, value).apply()
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

    /** Id of the rules last chosen in the menu (see GameRules.id). */
    var lastRulesId: String?
        get() = session.getString(KEY_RULES, null)
        set(value) {
            session.edit().putString(KEY_RULES, value).apply()
        }

    // ---- Settings ----------------------------------------------------------------------

    private val settingsPrefs = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun loadSettings(): Settings {
        val d = Settings()
        return Settings(
            effectsVolume = settingsPrefs.getFloat("effects_volume", d.effectsVolume).coerceIn(0f, 1f),
            musicVolume = settingsPrefs.getFloat("music_volume", d.musicVolume).coerceIn(0f, 1f),
            muted = settingsPrefs.getBoolean("muted", d.muted),
            haptics = settingsPrefs.getBoolean("haptics", d.haptics),
            theme = ThemeChoice.parse(settingsPrefs.getString("theme", null)),
        )
    }

    fun saveSettings(s: Settings) {
        settingsPrefs.edit()
            .putFloat("effects_volume", s.effectsVolume)
            .putFloat("music_volume", s.musicVolume)
            .putBoolean("muted", s.muted)
            .putBoolean("haptics", s.haptics)
            .putString("theme", s.theme.id)
            .apply()
    }

    /** Blocks until every queued write is on disk. For tests and for leaving the app. */
    fun flush() {
        io.submit {}.get(5, TimeUnit.SECONDS)
    }

    companion object {
        private const val KEY_ACTIVE = "active_game"
        private const val KEY_GRID = "grid_size"
        private const val KEY_RULES = "rules"
        private const val KEY_VERSUS = "versus"

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

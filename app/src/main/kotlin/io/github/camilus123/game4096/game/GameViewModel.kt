package io.github.camilus123.game4096.game

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import io.github.camilus123.game4096.core.Game
import io.github.camilus123.game4096.core.Rules
import io.github.camilus123.game4096.core.SaveCodec
import io.github.camilus123.game4096.core.ScoreEntry
import io.github.camilus123.game4096.data.GameKey
import io.github.camilus123.game4096.data.Storage

enum class GameStatus { PLAYING, WON, OVER }

enum class Screen { MENU, GAME, SCORES }

/**
 * Holds the game in play, mirrors it into Compose state, and keeps it saved.
 *
 * Saving rule: the game is saved after every move, so nothing is lost if the app is killed.
 * Scoring rule: a game goes on the high-score table once, when it ends or when the player
 * abandons it for a new one.
 */
class GameViewModel(
    application: Application,
    private val storage: Storage,
) : AndroidViewModel(application) {

    @Suppress("unused") // used by the default ViewModel factory
    constructor(application: Application) : this(application, Storage.get(application))

    var screen by mutableStateOf(Screen.MENU)
        private set

    var gridSize by mutableIntStateOf(storage.lastGridSize.coerceIn(4, 6))
        private set

    var game: Game? by mutableStateOf(null)
        private set

    var key: GameKey? = null
        private set

    var score by mutableLongStateOf(0L)
        private set

    var moves by mutableIntStateOf(0)
        private set

    /** Best score for this grid and mode, including the game in play. */
    var best by mutableLongStateOf(0L)
        private set

    var status by mutableStateOf(GameStatus.PLAYING)
        private set

    /** Rank the finished game took on the table (0 is first), or -1. */
    var finalRank by mutableIntStateOf(-1)
        private set

    private var winAcknowledged = false
    private var recorded = false
    private var tableBest = 0L

    init {
        // Coming back after the app was closed mid-game: go straight to that game.
        val active = storage.activeGame
        if (active != null && storage.hasGame(active)) {
            gridSize = active.size.coerceIn(4, 6)
            open(active, fresh = false)
        }
    }

    fun selectGrid(size: Int) {
        gridSize = size
        storage.lastGridSize = size
    }

    fun hasSavedGame(size: Int): Boolean = storage.hasGame(GameKey(size, GameKey.CLASSIC))

    /** Continues the saved Classic game for the selected grid, or starts one. */
    fun play() = open(GameKey(gridSize, GameKey.CLASSIC), fresh = false)

    /** Abandons the game in play (recording its score) and starts a new one on the same grid. */
    fun newGame() {
        val k = key ?: return
        record()
        open(k, fresh = true)
    }

    fun showMenu() {
        storage.activeGame = null
        screen = Screen.MENU
    }

    fun showScores() {
        screen = Screen.SCORES
    }

    fun scores(size: Int, modeId: String) = storage.scores(GameKey(size, modeId))

    /** Called by the board after every move that changed it. Not called per frame. */
    fun onMoved() {
        val g = game ?: return
        val k = key ?: return
        sync()
        if (g.isOver) {
            record()
            storage.clearGame(k)
        } else {
            storage.saveGame(k, g, winAcknowledged)
        }
    }

    fun keepPlaying() {
        winAcknowledged = true
        val g = game ?: return
        val k = key ?: return
        if (!g.isOver) storage.saveGame(k, g, true)
        sync()
    }

    private fun open(k: GameKey, fresh: Boolean) {
        val winExponent = Rules.defaultWinExponent(k.size)
        val saved = if (fresh) null else storage.loadGame(k)
        val g = if (saved != null) {
            winAcknowledged = saved.winAcknowledged
            SaveCodec.restore(saved, winExponent, undoLimit = 0)
        } else {
            winAcknowledged = false
            Game.new(k.size, seed = System.nanoTime() xor (System.currentTimeMillis() shl 20), winExponent = winExponent)
        }
        key = k
        game = g
        recorded = false
        finalRank = -1
        tableBest = storage.scores(k).best
        storage.saveGame(k, g, winAcknowledged)
        storage.activeGame = k
        screen = Screen.GAME
        sync()
    }

    private fun record() {
        val g = game ?: return
        val k = key ?: return
        if (recorded || g.score <= 0L) return
        recorded = true
        finalRank = storage.addScore(k, ScoreEntry(g.score, g.maxExponent, g.moves, System.currentTimeMillis()))
        tableBest = storage.scores(k).best
    }

    private fun sync() {
        val g = game ?: return
        score = g.score
        moves = g.moves
        best = if (g.score > tableBest) g.score else tableBest
        status = when {
            g.isOver -> GameStatus.OVER
            g.hasWon && !winAcknowledged -> GameStatus.WON
            else -> GameStatus.PLAYING
        }
    }
}

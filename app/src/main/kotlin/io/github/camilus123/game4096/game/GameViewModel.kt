package io.github.camilus123.game4096.game

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import io.github.camilus123.game4096.core.EndReason
import io.github.camilus123.game4096.core.Game
import io.github.camilus123.game4096.core.GameRules
import io.github.camilus123.game4096.core.ScoreEntry
import io.github.camilus123.game4096.core.Session
import io.github.camilus123.game4096.core.SessionStatus
import io.github.camilus123.game4096.data.GameKey
import io.github.camilus123.game4096.data.Storage

enum class Screen { MENU, GAME, SCORES }

/**
 * Holds the session in play, mirrors it into Compose state, keeps it saved, and runs the
 * Time attack clock.
 *
 * Saving rule: the game is saved after every move, so nothing is lost if the app is killed.
 * Scoring rule: a game goes on the high-score table once, when it ends or when the player
 * abandons it for a new one.
 */
class GameViewModel(
    application: Application,
    private val storage: Storage,
    private val clock: () -> Long,
) : AndroidViewModel(application) {

    @Suppress("unused") // used by the default ViewModel factory
    constructor(application: Application) :
        this(application, Storage.get(application), SystemClock::uptimeMillis)

    var screen by mutableStateOf(Screen.MENU)
        private set

    /** Grid size and rules chosen in the menu. */
    var gridSize by mutableIntStateOf(storage.lastGridSize.coerceIn(4, 6))
        private set

    var rules by mutableStateOf(GameRules.parse(storage.lastRulesId) ?: GameRules.CLASSIC)
        private set

    var session: Session? by mutableStateOf(null)
        private set

    val game: Game? get() = session?.game

    var key: GameKey? = null
        private set

    var score by mutableLongStateOf(0L)
        private set

    var moves by mutableIntStateOf(0)
        private set

    /** Best score for this grid and rules, including the game in play. */
    var best by mutableLongStateOf(0L)
        private set

    var status by mutableStateOf(SessionStatus.PLAYING)
        private set

    var endReason by mutableStateOf(EndReason.NONE)
        private set

    /** Rank the finished game took on the table (0 is first), or -1. */
    var finalRank by mutableIntStateOf(-1)
        private set

    /** Time left in Time attack, rounded up for display so the text changes only when it must. */
    var displayTimeMs by mutableLongStateOf(0L)
        private set

    var movesLeft by mutableIntStateOf(-1)
        private set

    var canUndo by mutableStateOf(false)
        private set

    /** Changes whenever the board changed without a swipe (undo), so the view redraws. */
    var boardVersion by mutableIntStateOf(0)
        private set

    private var recorded = false
    private var tableBest = 0L

    // Clock
    private val handler = Handler(Looper.getMainLooper())
    private var foreground = false
    private var ticking = false
    private var lastTick = 0L
    private val ticker = object : Runnable {
        override fun run() {
            if (!ticking) return
            tick()
            if (ticking) handler.postDelayed(this, TICK_MS)
        }
    }

    init {
        // Coming back after the app was closed mid-game: go straight to that game.
        val active = storage.activeGame
        val activeRules = GameRules.parse(active?.modeId)
        if (active != null && activeRules != null && active.size in 4..6 && storage.hasGame(active)) {
            gridSize = active.size
            rules = activeRules
            open(active, activeRules, fresh = false)
        }
    }

    // ---- Menu --------------------------------------------------------------------------

    fun selectGrid(size: Int) {
        gridSize = size
        storage.lastGridSize = size
    }

    fun selectRules(newRules: GameRules) {
        rules = newRules
        storage.lastRulesId = newRules.id
    }

    fun hasSavedGame(size: Int, forRules: GameRules): Boolean = storage.hasGame(GameKey(size, forRules.id))

    /** Continues the saved game for the selected grid and rules, or starts one. */
    fun play() = open(GameKey(gridSize, rules.id), rules, fresh = false)

    /** Abandons the game in play (recording its score) and starts a new one of the same kind. */
    fun newGame() {
        val k = key ?: return
        val s = session ?: return
        record()
        open(k, s.rules, fresh = true)
    }

    fun showMenu() {
        tick()
        persist()
        storage.activeGame = null
        screen = Screen.MENU
        updateTicker()
    }

    fun showScores() {
        screen = Screen.SCORES
    }

    fun scores(size: Int, forRules: GameRules) = storage.scores(GameKey(size, forRules.id))

    // ---- Play --------------------------------------------------------------------------

    /** Asked by the board before every move. Brings the clock up to date first. */
    fun canAcceptMove(): Boolean {
        tick()
        return session?.acceptsMoves == true
    }

    /** Called by the board after every move that changed it. Not called per frame. */
    fun onMoved() {
        if (!ticking) lastTick = clock() // the first move starts the clock from now
        sync()
        persist()
        updateTicker()
    }

    fun undo() {
        val s = session ?: return
        if (!s.undo()) return
        boardVersion++
        sync()
        persist()
    }

    fun keepPlaying() {
        val s = session ?: return
        s.winAcknowledged = true
        sync()
        persist()
    }

    /** The activity tells us when the app is visible; the clock only runs while it is. */
    fun setForeground(visible: Boolean) {
        if (visible == foreground) return
        tick()
        foreground = visible
        if (!visible) persist()
        updateTicker()
    }

    override fun onCleared() {
        ticking = false
        handler.removeCallbacks(ticker)
        super.onCleared()
    }

    /** Takes the time since the last call off the Time attack clock. Internal so tests can drive it. */
    internal fun tick() {
        val s = session ?: return
        val now = clock()
        if (ticking) {
            s.advanceClock(now - lastTick)
            val shown = roundForDisplay(s.timeLeftMs)
            if (shown != displayTimeMs) displayTimeMs = shown
            if (s.status != status) {
                sync()
                persist()
                updateTicker()
            }
        }
        lastTick = now
    }

    private fun updateTicker() {
        val shouldTick = foreground && screen == Screen.GAME && session?.clockRunning == true
        if (shouldTick == ticking) return
        ticking = shouldTick
        handler.removeCallbacks(ticker)
        if (shouldTick) {
            lastTick = clock()
            handler.postDelayed(ticker, TICK_MS)
        }
    }

    private fun open(k: GameKey, r: GameRules, fresh: Boolean) {
        val saved = if (fresh) null else storage.loadGame(k)
        val s = if (saved != null) {
            Session.restore(r, saved)
        } else {
            Session.new(r, k.size, seed = System.nanoTime() xor (System.currentTimeMillis() shl 20))
        }
        ticking = false
        handler.removeCallbacks(ticker)
        key = k
        session = s
        recorded = false
        finalRank = -1
        tableBest = storage.scores(k).best
        storage.activeGame = k
        screen = Screen.GAME
        boardVersion++
        sync()
        persist()
        updateTicker()
    }

    /** Saves the game, or, once it is over, scores it and removes the save. */
    private fun persist() {
        val s = session ?: return
        val k = key ?: return
        if (s.status == SessionStatus.OVER) {
            record()
            storage.clearGame(k)
        } else {
            storage.saveGame(k, s.toSavedGame())
        }
    }

    private fun record() {
        val s = session ?: return
        val k = key ?: return
        val g = s.game
        if (recorded || g.score <= 0L) return
        recorded = true
        finalRank = storage.addScore(k, ScoreEntry(g.score, g.maxExponent, g.moves, System.currentTimeMillis()))
        tableBest = storage.scores(k).best
    }

    private fun sync() {
        val s = session ?: return
        val g = s.game
        score = g.score
        moves = g.moves
        best = if (g.score > tableBest) g.score else tableBest
        status = s.status
        endReason = s.endReason
        movesLeft = s.movesLeft
        canUndo = s.rules.undoLimit > 0 && g.canUndo
        displayTimeMs = roundForDisplay(s.timeLeftMs)
    }

    /** Whole seconds while there is plenty of time, tenths in the last ten seconds. */
    private fun roundForDisplay(ms: Long): Long {
        val step = if (ms > 10_000L) 1000L else 100L
        return (ms + step - 1) / step * step
    }

    private companion object {
        const val TICK_MS = 50L
    }
}

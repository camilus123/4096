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
import io.github.camilus123.game4096.audio.AudioEngine
import io.github.camilus123.game4096.audio.GameAudio
import io.github.camilus123.game4096.audio.MusicTrack
import io.github.camilus123.game4096.audio.Sfx
import io.github.camilus123.game4096.core.Bot
import io.github.camilus123.game4096.core.Direction
import io.github.camilus123.game4096.core.EndReason
import io.github.camilus123.game4096.core.Game
import io.github.camilus123.game4096.core.GameRules
import io.github.camilus123.game4096.core.Mode
import io.github.camilus123.game4096.core.Outcome
import io.github.camilus123.game4096.core.Rules
import io.github.camilus123.game4096.core.ScoreEntry
import io.github.camilus123.game4096.core.Session
import io.github.camilus123.game4096.core.SessionStatus
import io.github.camilus123.game4096.core.VersusConfig
import io.github.camilus123.game4096.core.VersusMatch
import io.github.camilus123.game4096.core.VersusRecord
import io.github.camilus123.game4096.data.GameKey
import io.github.camilus123.game4096.data.Settings
import io.github.camilus123.game4096.data.Storage
import java.util.concurrent.Executor
import java.util.concurrent.Executors

enum class Screen { MENU, GAME, SCORES, SETTINGS }

/**
 * Holds what is being played (a single-player [Session] or a [VersusMatch]), mirrors it into
 * Compose state, keeps it saved, runs the clocks, and drives the computer opponent.
 *
 * Saving rule: the game is saved after every move, so nothing is lost if the app is killed.
 * Scoring rule: a game goes on the high-score table once, when it ends or when the player
 * abandons it for a new one. A Versus match is counted once, when it is decided.
 *
 * Threads: everything here runs on the main thread except the opponent's thinking, which
 * runs on [botExecutor] against a private copy of its board and reports back through the
 * main-thread handler.
 */
class GameViewModel(
    application: Application,
    private val storage: Storage,
    private val clock: () -> Long,
    private val botExecutor: Executor,
    private val audio: GameAudio,
) : AndroidViewModel(application) {

    @Suppress("unused") // used by the default ViewModel factory
    constructor(application: Application) : this(
        application,
        Storage.get(application),
        SystemClock::uptimeMillis,
        Executors.newSingleThreadExecutor { r -> Thread(r, "opponent").apply { priority = Thread.NORM_PRIORITY - 1 } },
        AudioEngine.get(application),
    )

    var screen by mutableStateOf(Screen.MENU)
        private set

    var settings by mutableStateOf(storage.loadSettings())
        private set

    // ---- Menu choices --------------------------------------------------------------------

    var gridSize by mutableIntStateOf(storage.lastGridSize.coerceIn(4, 6))
        private set

    /** Rules for the single-player modes. Kept while Versus is selected. */
    var rules by mutableStateOf(GameRules.parse(storage.lastRulesId) ?: GameRules.CLASSIC)
        private set

    var mode by mutableStateOf(if (storage.lastRulesId == Mode.VERSUS.id) Mode.VERSUS else rules.mode)
        private set

    var versusConfig by mutableStateOf(VersusConfig.parse(storage.lastVersusId) ?: VersusConfig.DEFAULT)
        private set

    // ---- What is being played ------------------------------------------------------------

    var session: Session? by mutableStateOf(null)
        private set

    var match: VersusMatch? by mutableStateOf(null)
        private set

    /** The player's board, in either kind of game. */
    val game: Game? get() = session?.game ?: match?.player

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

    /** Time left on the clock, rounded up for display so the text changes only when it must. */
    var displayTimeMs by mutableLongStateOf(0L)
        private set

    var movesLeft by mutableIntStateOf(-1)
        private set

    var canUndo by mutableStateOf(false)
        private set

    /** Changes whenever the board changed without a swipe (undo), so the view redraws. */
    var boardVersion by mutableIntStateOf(0)
        private set

    // ---- Versus state --------------------------------------------------------------------

    var opponentScore by mutableLongStateOf(0L)
        private set

    /** Exponent of the opponent's biggest tile. */
    var opponentTile by mutableIntStateOf(0)
        private set

    /** Exponent of the player's biggest tile. */
    var playerTile by mutableIntStateOf(0)
        private set

    var outcome by mutableStateOf(Outcome.UNDECIDED)
        private set

    var matchStarted by mutableStateOf(false)
        private set

    /** Wins and losses on this grid against this difficulty. */
    var versusRecord by mutableStateOf(VersusRecord())
        private set

    /** Set by the opponent's board view; called on the main thread after each opponent move. */
    var onOpponentMoved: (() -> Unit)? = null

    private var recorded = false
    private var tableBest = 0L

    // Opponent
    private var bot: Bot? = null
    private var botGeneration = 0
    private var botPending = false

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
        if (active != null && active.size in 4..6) {
            if (active.modeId == GameKey.VERSUS) {
                val saved = storage.loadVersus(active.size)
                if (saved != null && !saved.isDecided) {
                    gridSize = active.size
                    mode = Mode.VERSUS
                    versusConfig = saved.config
                    openVersus(active.size, saved.config, fresh = false)
                }
            } else {
                val activeRules = GameRules.parse(active.modeId)
                if (activeRules != null && storage.hasGame(active)) {
                    gridSize = active.size
                    rules = activeRules
                    mode = activeRules.mode
                    open(active, activeRules, fresh = false)
                }
            }
        }
    }

    init {
        audio.setVolumes(settings.effectsVolume, settings.musicVolume, settings.muted)
        updateMusic()
    }

    // ---- Settings ----------------------------------------------------------------------

    /** Applies and saves new settings. [tap] is false for sliders, which change many times per drag. */
    fun updateSettings(new: Settings, tap: Boolean = true) {
        settings = new
        storage.saveSettings(new)
        audio.setVolumes(new.effectsVolume, new.musicVolume, new.muted)
        if (tap) audio.play(Sfx.TAP)
    }

    /** A sample of the effects at the current volume, for the settings screen. */
    fun previewEffects() = audio.playMerge(5)

    fun showSettings() {
        audio.play(Sfx.TAP)
        screen = Screen.SETTINGS
    }

    private fun updateMusic() {
        audio.setMusic(
            when {
                screen != Screen.GAME -> MusicTrack.MENU
                session?.rules?.mode == Mode.ZEN -> MusicTrack.ZEN
                else -> MusicTrack.GAME
            },
        )
    }

    // ---- Menu --------------------------------------------------------------------------

    fun selectGrid(size: Int) {
        audio.play(Sfx.TAP)
        gridSize = size
        storage.lastGridSize = size
    }

    /** Picks a mode. Time attack and Limited moves start on their middle option. */
    fun selectMode(newMode: Mode) {
        audio.play(Sfx.TAP)
        if (newMode == Mode.VERSUS) {
            mode = newMode
            storage.lastRulesId = Mode.VERSUS.id
        } else if (newMode != rules.mode) {
            selectRules(
                when (newMode) {
                    Mode.TIME_ATTACK -> GameRules(newMode, GameRules.TIME_VARIANTS[1])
                    Mode.LIMITED_MOVES -> GameRules(newMode, GameRules.MOVE_VARIANTS[1])
                    else -> GameRules(newMode)
                },
                tap = false,
            )
        } else {
            selectRules(rules, tap = false)
        }
    }

    fun selectRules(newRules: GameRules, tap: Boolean = true) {
        if (tap) audio.play(Sfx.TAP)
        rules = newRules
        mode = newRules.mode
        storage.lastRulesId = newRules.id
    }

    fun selectVersus(config: VersusConfig) {
        audio.play(Sfx.TAP)
        versusConfig = config
        storage.lastVersusId = config.id
    }

    fun hasSavedGame(size: Int, forRules: GameRules): Boolean = storage.hasGame(GameKey(size, forRules.id))

    /** True if Play would continue an unfinished game for the current menu choices. */
    fun canContinue(): Boolean =
        if (mode == Mode.VERSUS) {
            val saved = storage.loadVersus(gridSize)
            saved != null && !saved.isDecided && saved.config == versusConfig
        } else {
            hasSavedGame(gridSize, rules)
        }

    /** Continues the saved game for the current menu choices, or starts one. */
    fun play() {
        audio.play(Sfx.TAP)
        if (mode == Mode.VERSUS) {
            openVersus(gridSize, versusConfig, fresh = false)
        } else {
            open(GameKey(gridSize, rules.id), rules, fresh = false)
        }
    }

    /** Starts over with the same settings. An abandoned single-player game is scored first. */
    fun newGame() {
        audio.play(Sfx.TAP)
        val k = key ?: return
        val m = match
        val s = session
        if (m != null) {
            openVersus(k.size, m.config, fresh = true)
        } else if (s != null) {
            record()
            open(k, s.rules, fresh = true)
        }
    }

    fun showMenu() {
        audio.play(Sfx.TAP)
        tick()
        persist()
        cancelBot()
        storage.activeGame = null
        screen = Screen.MENU
        updateTicker()
        updateMusic()
    }

    fun showScores() {
        audio.play(Sfx.TAP)
        screen = Screen.SCORES
    }

    fun scores(size: Int, forRules: GameRules) = storage.scores(GameKey(size, forRules.id))

    fun versusRecord(size: Int, difficulty: io.github.camilus123.game4096.core.Difficulty) =
        storage.versusRecord(size, difficulty)

    // ---- Play --------------------------------------------------------------------------

    /** Asked by the board before every move. Brings the clock up to date first. */
    fun canAcceptMove(): Boolean {
        tick()
        val m = match
        return if (m != null) m.playerCanMove else session?.acceptsMoves == true
    }

    /** Called by the board after every move that changed it. Not called per frame. */
    fun onMoved() {
        if (!ticking) lastTick = clock() // the first move starts the clock from now
        moveSounds()
        sync()
        persist()
        updateTicker()
        scheduleBot() // the player's first move is what wakes the opponent
    }

    fun undo() {
        val s = session ?: return
        if (!s.undo()) return
        audio.play(Sfx.UNDO)
        boardVersion++
        sync()
        persist()
    }

    fun keepPlaying() {
        audio.play(Sfx.TAP)
        val s = session ?: return
        s.winAcknowledged = true
        sync()
        persist()
    }

    /** The activity tells us when the app is visible; clocks and the opponent only run while it is. */
    fun setForeground(visible: Boolean) {
        if (visible == foreground) return
        tick()
        foreground = visible
        audio.setActive(visible)
        if (visible) {
            scheduleBot()
        } else {
            persist()
            cancelBot()
        }
        updateTicker()
    }

    override fun onCleared() {
        ticking = false
        cancelBot()
        handler.removeCallbacksAndMessages(null)
        onOpponentMoved = null
        super.onCleared()
    }

    /**
     * The sounds of the player's move: the slide at once, then the merge (or, with no merge,
     * the quiet blip of the new tile) timed to land when the tiles arrive on screen.
     */
    private fun moveSounds() {
        val g = game ?: return
        audio.play(Sfx.SLIDE)
        var biggest = 0
        val trace = g.lastTrace
        for (i in 0 until g.cellCount) {
            if (trace.merged[i]) {
                val e = g.exponentAt(i)
                if (e > biggest) biggest = e
            }
        }
        pendingMerge = biggest
        handler.removeCallbacks(arrivalSound)
        handler.postDelayed(arrivalSound, ARRIVAL_SOUND_DELAY_MS)
    }

    private var pendingMerge = 0
    private val arrivalSound = Runnable {
        if (pendingMerge > 0) audio.playMerge(pendingMerge) else audio.play(Sfx.SPAWN)
    }

    /** Takes the time since the last call off whichever clock is running. Internal so tests can drive it. */
    internal fun tick() {
        val now = clock()
        if (ticking) {
            val elapsed = now - lastTick
            val s = session
            val m = match
            if (s != null) {
                s.advanceClock(elapsed)
                showTime(s.timeLeftMs)
                if (s.status != status) afterClockChange()
            } else if (m != null) {
                m.advanceClock(elapsed)
                showTime(m.timeLeftMs)
                if (m.outcome != outcome) afterClockChange()
            }
        }
        lastTick = now
    }

    private fun afterClockChange() {
        sync()
        persist()
        updateTicker()
    }

    private fun showTime(ms: Long) {
        val shown = roundForDisplay(ms)
        if (shown != displayTimeMs) displayTimeMs = shown
    }

    private fun updateTicker() {
        val clockRunning = session?.clockRunning == true || match?.clockRunning == true
        val shouldTick = foreground && screen == Screen.GAME && clockRunning
        if (shouldTick == ticking) return
        ticking = shouldTick
        handler.removeCallbacks(ticker)
        if (shouldTick) {
            lastTick = clock()
            handler.postDelayed(ticker, TICK_MS)
        }
    }

    // ---- Opening games -------------------------------------------------------------------

    private fun newSeed(): Long = System.nanoTime() xor (System.currentTimeMillis() shl 20)

    private fun open(k: GameKey, r: GameRules, fresh: Boolean) {
        val saved = if (fresh) null else storage.loadGame(k)
        val s = if (saved != null) Session.restore(r, saved) else Session.new(r, k.size, newSeed())
        resetPlayState()
        key = k
        match = null
        session = s
        tableBest = storage.scores(k).best
        show(k)
    }

    private fun openVersus(size: Int, config: VersusConfig, fresh: Boolean) {
        val saved = if (fresh) null else storage.loadVersus(size)
        val usable = saved != null && !saved.isDecided && saved.config == config
        val m = if (usable) saved!! else VersusMatch.new(config, size, newSeed())
        resetPlayState()
        key = GameKey(size, GameKey.VERSUS)
        session = null
        match = m
        bot = Bot(config.difficulty, newSeed())
        versusRecord = storage.versusRecord(size, config.difficulty)
        show(key!!)
        scheduleBot()
    }

    private fun resetPlayState() {
        ticking = false
        handler.removeCallbacks(ticker)
        cancelBot()
        recorded = false
        finalRank = -1
        tableBest = 0L
    }

    private fun show(k: GameKey) {
        storage.activeGame = k
        screen = Screen.GAME
        boardVersion++
        opening = true
        sync()
        opening = false
        persist()
        updateTicker()
        updateMusic()
    }

    // ---- The opponent --------------------------------------------------------------------

    /**
     * Asks the opponent for its next move if it is its turn to think. The thinking happens on
     * [botExecutor] against a copy of its board; the move is applied back here on the main
     * thread after a human-like pause, so the search can never cost the player a frame.
     */
    private fun scheduleBot() {
        val m = match ?: return
        val b = bot ?: return
        if (botPending || !foreground || screen != Screen.GAME || !m.botCanMove) return
        botPending = true
        val generation = botGeneration
        val size = m.bot.size
        val view = IntArray(m.bot.cellCount)
        m.bot.copyCellsInto(view)
        val asked = clock()
        botExecutor.execute {
            val dir = b.chooseMove(view, size)
            val pause = b.nextDelayMs() - (clock() - asked)
            handler.postDelayed({ onBotDecided(generation, dir) }, if (pause > 0L) pause else 0L)
        }
    }

    private fun onBotDecided(generation: Int, dir: Direction?) {
        if (generation != botGeneration) return // the match was paused, left or replaced meanwhile
        botPending = false
        val m = match ?: return
        tick()
        if (dir == null || !m.botCanMove || !foreground || screen != Screen.GAME) return
        if (m.bot.move(dir) == Rules.NO_MOVE) return
        onOpponentMoved?.invoke()
        sync()
        persist()
        updateTicker()
        scheduleBot()
    }

    private fun cancelBot() {
        botGeneration++
        botPending = false
    }

    // ---- Saving and scoring --------------------------------------------------------------

    /** Saves what is being played, or, once it is over, scores it and removes the save. */
    private fun persist() {
        val k = key ?: return
        val s = session
        val m = match
        if (s != null) {
            if (s.status == SessionStatus.OVER) {
                record()
                storage.clearGame(k)
            } else {
                storage.saveGame(k, s.toSavedGame())
            }
        } else if (m != null) {
            if (m.isDecided) {
                if (!recorded) {
                    recorded = true
                    versusRecord = storage.addVersusResult(k.size, m.config.difficulty, m.outcome)
                }
                storage.clearVersus(k.size)
            } else {
                storage.saveVersus(k.size, m.encode())
            }
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

    /** True while a game is being opened: a restored end state must not replay its fanfare. */
    private var opening = false

    private fun sync() {
        val s = session
        val m = match
        val wasPlaying = status == SessionStatus.PLAYING && !opening
        if (s != null) {
            val g = s.game
            score = g.score
            moves = g.moves
            best = if (g.score > tableBest) g.score else tableBest
            status = s.status
            endReason = s.endReason
            movesLeft = s.movesLeft
            canUndo = s.rules.undoLimit > 0 && g.canUndo
            displayTimeMs = roundForDisplay(s.timeLeftMs)
            outcome = Outcome.UNDECIDED
            if (wasPlaying && status != SessionStatus.PLAYING) {
                audio.play(if (status == SessionStatus.WON) Sfx.WIN else Sfx.OVER)
            }
        } else if (m != null) {
            score = m.player.score
            moves = m.player.moves
            playerTile = m.player.maxExponent
            opponentScore = m.bot.score
            opponentTile = m.bot.maxExponent
            outcome = m.outcome
            matchStarted = m.started
            displayTimeMs = roundForDisplay(m.timeLeftMs)
            status = if (m.isDecided) SessionStatus.OVER else SessionStatus.PLAYING
            canUndo = false
            movesLeft = -1
            if (wasPlaying && status != SessionStatus.PLAYING) {
                audio.play(if (outcome == Outcome.PLAYER_WON) Sfx.WIN else Sfx.OVER)
            }
        }
    }

    /** Whole seconds while there is plenty of time, tenths in the last ten seconds. */
    private fun roundForDisplay(ms: Long): Long {
        val step = if (ms > 10_000L) 1000L else 100L
        return (ms + step - 1) / step * step
    }

    private companion object {
        const val TICK_MS = 50L

        /** A little under the board's slide time, so the sound meets the tiles. */
        const val ARRIVAL_SOUND_DELAY_MS = 95L
    }
}

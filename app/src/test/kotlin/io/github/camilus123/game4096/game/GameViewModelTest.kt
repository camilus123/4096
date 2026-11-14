package io.github.camilus123.game4096.game

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.camilus123.game4096.core.Direction
import android.os.Looper
import android.os.SystemClock
import io.github.camilus123.game4096.audio.GameAudio
import io.github.camilus123.game4096.audio.MusicTrack
import io.github.camilus123.game4096.audio.Sfx
import io.github.camilus123.game4096.core.Difficulty
import io.github.camilus123.game4096.data.HapticLevel
import io.github.camilus123.game4096.data.ThemeChoice
import io.github.camilus123.game4096.core.EndReason
import io.github.camilus123.game4096.core.Outcome
import io.github.camilus123.game4096.core.VersusConfig
import io.github.camilus123.game4096.core.VersusRule
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import io.github.camilus123.game4096.core.Game
import io.github.camilus123.game4096.core.GameRules
import io.github.camilus123.game4096.core.Mode
import io.github.camilus123.game4096.core.SessionStatus
import io.github.camilus123.game4096.data.GameKey
import io.github.camilus123.game4096.data.Storage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Saving, resuming and high scores, including across an "app restart": each restart is a
 * new Storage and a new view model reading what the previous ones wrote to disk.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameViewModelTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    /** Records what the game asked the audio to do. */
    private class RecordingAudio : GameAudio {
        val effects = ArrayList<Sfx>()
        val merges = ArrayList<Int>()
        var track = MusicTrack.NONE
        var inForeground = false
        var effectsVolume = -1f
        var musicVolume = -1f
        var muted = false
        override fun play(sfx: Sfx) { effects.add(sfx) }
        override fun playMerge(exponent: Int) { merges.add(exponent) }
        override fun setMusic(track: MusicTrack) { this.track = track }
        override fun setVolumes(effects: Float, music: Float, muted: Boolean) {
            effectsVolume = effects; musicVolume = music; this.muted = muted
        }
        override fun setActive(active: Boolean) { inForeground = active }
    }

    private val audio = RecordingAudio()

    /** Every vibration asked for, as exponent to strength. */
    private val felt = ArrayList<Pair<Int, HapticLevel>>()
    private val vibrations: (Int, HapticLevel) -> Unit = { e, level -> felt.add(e to level) }

    /** Fake time for the Time attack clock; tests move it forward by hand. */
    private var now = 1_000_000L

    private val classic4 = GameKey(4, GameRules.CLASSIC.id)

    private fun launch(): Pair<GameViewModel, Storage> {
        val storage = Storage(app)
        val vm = GameViewModel(app, storage, { now }, Runnable::run, audio, vibrations)
        vm.setForeground(true)
        return vm to storage
    }

    /** Lets [ms] of fake time pass and has the view model notice. */
    private fun wait(vm: GameViewModel, ms: Long) {
        now += ms
        vm.tick()
    }

    private fun Game.cells(): IntArray = IntArray(cellCount).also { copyCellsInto(it) }

    /** Makes up to [count] moves the way the board view does: move, then tell the view model. */
    private fun play(vm: GameViewModel, count: Int) {
        val g = vm.game!!
        var made = 0
        var i = 0
        while (made < count && !g.isOver) {
            if (!vm.canAcceptMove()) break // what the board view asks before every move
            if (g.move(Direction.entries[i++ % 4]) >= 0) {
                vm.onMoved()
                made++
            }
        }
    }

    private fun playToTheEnd(vm: GameViewModel) = play(vm, Int.MAX_VALUE)

    @Test fun firstLaunchShowsTheMenu() {
        val (vm, _) = launch()
        assertEquals(Screen.MENU, vm.screen)
        assertNull(vm.game)
        assertEquals(4, vm.gridSize)
        assertFalse(vm.hasSavedGame(4, GameRules.CLASSIC))
    }

    @Test fun anUnfinishedGameComesBackAfterARestart() {
        val (vm, storage) = launch()
        vm.selectGrid(5)
        vm.play()
        play(vm, 25)
        val cells = vm.game!!.cells()
        val score = vm.score
        storage.flush()

        val (again, _) = launch() // the app was killed while the game was on screen
        assertEquals(Screen.GAME, again.screen)
        assertEquals(5, again.gridSize)
        assertEquals(score, again.score)
        assertEquals(25, again.moves)
        assertTrue(cells.contentEquals(again.game!!.cells()))
    }

    @Test fun leavingToTheMenuKeepsTheGameButOpensTheMenuNextTime() {
        val (vm, storage) = launch()
        vm.play()
        play(vm, 10)
        val cells = vm.game!!.cells()
        vm.showMenu()
        storage.flush()

        val (again, _) = launch()
        assertEquals(Screen.MENU, again.screen)
        assertTrue(again.hasSavedGame(4, GameRules.CLASSIC))
        assertFalse(again.hasSavedGame(5, GameRules.CLASSIC))
        again.play()
        assertEquals(10, again.moves)
        assertTrue(cells.contentEquals(again.game!!.cells()))
    }

    @Test fun eachGridHasItsOwnSavedGame() {
        val (vm, storage) = launch()
        vm.selectGrid(4); vm.play(); play(vm, 7); vm.showMenu()
        vm.selectGrid(6); vm.play(); play(vm, 12); vm.showMenu()
        storage.flush()

        val (again, _) = launch()
        again.selectGrid(4); again.play()
        assertEquals(7, again.moves)
        assertEquals(4, again.game!!.size)
        again.showMenu()
        again.selectGrid(6); again.play()
        assertEquals(12, again.moves)
        assertEquals(6, again.game!!.size)
    }

    @Test fun aFinishedGameIsScoredOnceAndNotResumed() {
        val (vm, storage) = launch()
        vm.play()
        playToTheEnd(vm)
        assertEquals(SessionStatus.OVER, vm.status)
        val score = vm.score
        assertTrue(score > 0)
        assertEquals(0, vm.finalRank)
        assertEquals(score, vm.best)
        storage.flush()

        val (again, storage2) = launch()
        assertEquals("a finished game is not reopened", Screen.MENU, again.screen)
        assertFalse(again.hasSavedGame(4, GameRules.CLASSIC))
        val table = storage2.scores(classic4)
        assertEquals(1, table.entries.size)
        assertEquals(score, table.best)
        again.play()
        assertEquals(0L, again.score)
        assertEquals("the best score is shown from the first move", score, again.best)
    }

    @Test fun scoresAreKeptPerGrid() {
        val (vm, storage) = launch()
        vm.selectGrid(4); vm.play(); playToTheEnd(vm); vm.showMenu()
        val small = vm.scores(4, GameRules.CLASSIC).best
        vm.selectGrid(5); vm.play(); playToTheEnd(vm); vm.showMenu()
        storage.flush()

        val (_, fresh) = launch()
        assertEquals(small, fresh.scores(classic4).best)
        assertEquals(1, fresh.scores(classic4).entries.size)
        assertEquals(1, fresh.scores(GameKey(5, GameRules.CLASSIC.id)).entries.size)
        assertTrue(fresh.scores(GameKey(6, GameRules.CLASSIC.id)).isEmpty)
    }

    @Test fun startingANewGameRecordsTheAbandonedOne() {
        val (vm, storage) = launch()
        vm.play()
        play(vm, 30)
        val abandoned = vm.score
        assertTrue(abandoned > 0)
        val old = vm.game
        vm.newGame()
        assertNotSame(old, vm.game)
        assertEquals(0L, vm.score)
        assertEquals(0, vm.moves)
        assertEquals(abandoned, vm.best)
        assertEquals(1, storage.scores(classic4).entries.size)

        vm.newGame() // a game with no points is not worth a table row
        assertEquals(1, storage.scores(classic4).entries.size)
    }

    @Test fun bestFollowsTheScoreOnceItIsBeaten() {
        val (vm, _) = launch()
        vm.play()
        assertEquals(0L, vm.best)
        play(vm, 30)
        assertEquals(vm.score, vm.best)
    }

    @Test fun aDamagedSaveStartsANewGame() {
        app.getSharedPreferences("saves", 0).edit().putString("classic_4", "1;4;0;not a save").commit()
        app.getSharedPreferences("session", 0).edit().putString("active_game", "classic_4").commit()
        val (vm, _) = launch()
        assertEquals(Screen.MENU, vm.screen)
        assertFalse(vm.hasSavedGame(4, GameRules.CLASSIC))
        vm.play()
        assertNotNull(vm.game)
        assertEquals(0, vm.moves)
    }

    // ---- Modes -------------------------------------------------------------------------

    @Test fun theChosenModeIsRememberedAndHasItsOwnSaveSlot() {
        val (vm, storage) = launch()
        val timed = GameRules(Mode.TIME_ATTACK, 60)
        vm.selectRules(timed)
        vm.selectGrid(5)
        vm.play()
        play(vm, 5)
        vm.showMenu()
        storage.flush()

        val (again, _) = launch()
        assertEquals(timed, again.rules)
        assertEquals(5, again.gridSize)
        assertTrue(again.hasSavedGame(5, timed))
        assertFalse(again.hasSavedGame(5, GameRules.CLASSIC))
        assertFalse(again.hasSavedGame(5, GameRules(Mode.TIME_ATTACK, 180)))
        assertFalse(again.hasSavedGame(4, timed))
    }

    @Test fun zenUndoWorksAndSurvivesARestart() {
        val (vm, storage) = launch()
        vm.selectRules(GameRules.ZEN)
        vm.play()
        assertFalse(vm.canUndo)
        play(vm, 12)
        assertTrue(vm.canUndo)
        val versionBefore = vm.boardVersion
        vm.undo()
        assertEquals(11, vm.moves)
        assertTrue("the board is told to redraw", vm.boardVersion != versionBefore)
        storage.flush()

        val (again, _) = launch()
        assertEquals(Screen.GAME, again.screen)
        assertEquals(GameRules.ZEN, again.session!!.rules)
        assertEquals(11, again.moves)
        repeat(11) { again.undo() }
        assertEquals(0, again.moves)
        assertEquals(0L, again.score)
        assertFalse(again.canUndo)
    }

    @Test fun zenGetsStuckInsteadOfGameOverAndUndoGetsOut() {
        val (vm, storage) = launch()
        vm.selectRules(GameRules.ZEN)
        vm.play()
        playToTheEnd(vm)
        assertEquals(SessionStatus.STUCK, vm.status)
        assertTrue("a stuck Zen game is not scored yet", storage.scores(GameKey(4, "zen")).isEmpty)
        assertTrue(vm.hasSavedGame(4, GameRules.ZEN))
        vm.undo()
        assertEquals(SessionStatus.PLAYING, vm.status)

        val score = vm.score
        vm.newGame() // leaving a Zen game is what puts it on the table
        assertEquals(score, storage.scores(GameKey(4, "zen")).best)
        assertTrue("Zen scores stay out of the Classic table", storage.scores(classic4).isEmpty)
    }

    @Test fun timeAttackCountsDownOnlyWhilePlayingInTheForeground() {
        val (vm, _) = launch()
        vm.selectRules(GameRules(Mode.TIME_ATTACK, 60))
        vm.play()
        assertEquals(60_000L, vm.displayTimeMs)
        wait(vm, 5_000)
        assertEquals("the clock waits for the first move", 60_000L, vm.displayTimeMs)

        play(vm, 1)
        wait(vm, 10_000)
        assertEquals(50_000L, vm.displayTimeMs)

        vm.setForeground(false) // app goes to the background
        now += 30_000
        vm.setForeground(true)
        wait(vm, 0)
        assertEquals("no time passes in the background", 50_000L, vm.displayTimeMs)

        vm.showMenu()
        now += 30_000
        vm.play()
        wait(vm, 0)
        assertEquals("no time passes in the menu", 50_000L, vm.displayTimeMs)
        wait(vm, 2_000)
        assertEquals(48_000L, vm.displayTimeMs)
    }

    @Test fun timeAttackClockSurvivesARestart() {
        val (vm, storage) = launch()
        vm.selectRules(GameRules(Mode.TIME_ATTACK, 180))
        vm.play()
        play(vm, 3)
        wait(vm, 75_000)
        vm.setForeground(false)
        storage.flush()

        now += 500_000 // the app was closed for a long time
        val (again, _) = launch()
        assertEquals(Screen.GAME, again.screen)
        assertEquals(105_000L, again.displayTimeMs)
    }

    @Test fun timeAttackEndsOnTimeAndIsScored() {
        val (vm, storage) = launch()
        val rules = GameRules(Mode.TIME_ATTACK, 60)
        vm.selectRules(rules)
        vm.play()
        play(vm, 20)
        val score = vm.score
        wait(vm, 59_000)
        assertEquals(SessionStatus.PLAYING, vm.status)
        assertEquals(1_000L, vm.displayTimeMs)
        wait(vm, 1_500)
        assertEquals(SessionStatus.OVER, vm.status)
        assertEquals(EndReason.TIME_UP, vm.endReason)
        assertEquals(0L, vm.displayTimeMs)
        assertFalse("no move is accepted after time is up", vm.canAcceptMove())
        assertEquals(score, storage.scores(GameKey(4, rules.id)).best)
        assertFalse(vm.hasSavedGame(4, rules))
        assertTrue("other durations keep their own tables", storage.scores(GameKey(4, "time_180")).isEmpty)
    }

    @Test fun aMoveArrivingAfterTheClockRanOutIsRefused() {
        val (vm, _) = launch()
        vm.selectRules(GameRules(Mode.TIME_ATTACK, 60))
        vm.play()
        play(vm, 2)
        now += 61_000 // time runs out between two clock ticks
        assertFalse(vm.canAcceptMove())
        assertEquals(SessionStatus.OVER, vm.status)
        assertEquals(2, vm.moves)
    }

    @Test fun limitedMovesStopsExactlyAtTheBudget() {
        val (vm, storage) = launch()
        val rules = GameRules(Mode.LIMITED_MOVES, 50)
        vm.selectRules(rules)
        vm.selectGrid(6)
        vm.play()
        assertEquals(50, vm.movesLeft)
        play(vm, 49)
        assertEquals(1, vm.movesLeft)
        assertEquals(SessionStatus.PLAYING, vm.status)
        play(vm, 10)
        assertEquals(50, vm.moves)
        assertEquals(0, vm.movesLeft)
        assertEquals(SessionStatus.OVER, vm.status)
        assertEquals(EndReason.OUT_OF_MOVES, vm.endReason)
        assertEquals(vm.score, storage.scores(GameKey(6, rules.id)).best)
    }

    @Test fun aClassicSaveFromTheFirstReleaseStillLoads() {
        app.getSharedPreferences("saves", 0).edit()
            .putString("classic_4", "1;4;0;40,7,-99,0 1 0 0 0 0 2 0 0 0 0 0 0 0 0 3").commit()
        val (vm, _) = launch()
        assertTrue(vm.hasSavedGame(4, GameRules.CLASSIC))
        vm.play()
        assertEquals(40L, vm.score)
        assertEquals(7, vm.moves)
    }

    // ---- Versus AI ---------------------------------------------------------------------

    private val looper get() = shadowOf(Looper.getMainLooper())

    /** As [launch], but on Robolectric's clock, so the opponent's pauses pass when the looper is idled. */
    private fun launchVersus(config: VersusConfig, size: Int = 4): Pair<GameViewModel, Storage> {
        val storage = Storage(app)
        val vm = GameViewModel(app, storage, SystemClock::uptimeMillis, Runnable::run, audio, vibrations)
        vm.setForeground(true)
        vm.selectGrid(size)
        vm.selectMode(Mode.VERSUS)
        vm.selectVersus(config)
        return vm to storage
    }

    private fun letPass(seconds: Long) = looper.idleFor(Duration.ofSeconds(seconds))

    private val easyRace = VersusConfig(VersusRule.RACE, 256, Difficulty.EASY)

    @Test fun theOpponentWaitsForThePlayersFirstMoveThenPlaysAtAHumanPace() {
        val (vm, _) = launchVersus(easyRace)
        vm.play()
        val match = vm.match!!
        var seen = 0
        vm.onOpponentMoved = { seen++ }
        letPass(10)
        assertEquals("the opponent has not started", 0, match.bot.moves)
        assertFalse(vm.matchStarted)

        play(vm, 1)
        assertTrue(vm.matchStarted)
        letPass(10)
        val made = match.bot.moves
        assertTrue("in 10 s an easy opponent makes roughly 9 to 15 moves, made $made", made in 7..16)
        assertEquals("the small board is told about every move", made, seen)
        assertEquals(match.bot.score, vm.opponentScore)
    }

    @Test fun harderOpponentsMoveFaster() {
        fun movesIn20s(d: Difficulty): Int {
            Storage.resetForTests()
            val (vm, _) = launchVersus(VersusConfig(VersusRule.LAST_STANDING, 0, d), size = 6)
            vm.play()
            play(vm, 1)
            letPass(20)
            val made = vm.match!!.bot.moves
            vm.showMenu()
            return made
        }
        val easy = movesIn20s(Difficulty.EASY)
        val medium = movesIn20s(Difficulty.MEDIUM)
        val hard = movesIn20s(Difficulty.HARD)
        assertTrue("easy $easy, medium $medium, hard $hard", easy < medium && medium < hard)
    }

    @Test fun theOpponentStopsWhileTheAppIsInTheBackgroundOrTheMenuIsOpen() {
        val (vm, _) = launchVersus(VersusConfig(VersusRule.LAST_STANDING, 0, Difficulty.MEDIUM), size = 5)
        vm.play()
        play(vm, 1)
        letPass(5)
        val match = vm.match!!
        val before = match.bot.moves
        assertTrue(before > 0)

        vm.setForeground(false)
        letPass(30)
        assertEquals(before, match.bot.moves)
        vm.setForeground(true)
        letPass(5)
        val afterResume = match.bot.moves
        assertTrue(afterResume > before)

        vm.showMenu()
        letPass(30)
        assertEquals(afterResume, match.bot.moves)
    }

    @Test fun aRaceIsDecidedCountedOnceAndNotResumed() {
        val (vm, storage) = launchVersus(easyRace)
        vm.play()
        play(vm, 1) // then the player does nothing: the opponent either makes 256 or gets stuck
        var waited = 0
        while (vm.outcome == Outcome.UNDECIDED && waited++ < 200) letPass(10)
        assertTrue("the match never ended", vm.outcome != Outcome.UNDECIDED)
        val match = vm.match!!
        if (match.bot.maxExponent >= 8) assertEquals(Outcome.BOT_WON, vm.outcome) else assertEquals(Outcome.PLAYER_WON, vm.outcome)
        assertFalse("no moves after the result", vm.canAcceptMove())
        val botMoves = match.bot.moves
        letPass(20)
        assertEquals("the opponent stops too", botMoves, match.bot.moves)
        assertEquals(1, vm.versusRecord.played)
        assertFalse(vm.canContinue())
        storage.flush()

        val storage2 = Storage(app)
        val again = GameViewModel(app, storage2, SystemClock::uptimeMillis, Runnable::run, audio, vibrations)
        assertEquals("a finished match is not reopened", Screen.MENU, again.screen)
        assertEquals(Mode.VERSUS, again.mode)
        assertEquals(easyRace, again.versusConfig)
        assertEquals(1, storage2.versusRecord(4, Difficulty.EASY).played)
        assertEquals("other difficulties and grids have their own record", 0, storage2.versusRecord(4, Difficulty.HARD).played)
        assertEquals(0, storage2.versusRecord(5, Difficulty.EASY).played)
    }

    @Test fun aTimedMatchEndsOnTheClockAndGoesToTheHigherScore() {
        val (vm, _) = launchVersus(VersusConfig(VersusRule.TIMER, 120, Difficulty.MEDIUM), size = 5)
        vm.play()
        assertEquals(120_000L, vm.displayTimeMs)
        letPass(30)
        assertEquals("the clock waits for the first move", 120_000L, vm.displayTimeMs)
        play(vm, 1)
        letPass(60)
        assertEquals(Outcome.UNDECIDED, vm.outcome)
        assertTrue("about a minute left, shows ${vm.displayTimeMs}", vm.displayTimeMs in 59_000L..61_000L)
        letPass(61)
        assertEquals(0L, vm.displayTimeMs)
        assertEquals("the opponent scored for two minutes, the player made one move", Outcome.BOT_WON, vm.outcome)
        assertEquals(1, vm.versusRecord.losses)
    }

    @Test fun anUnfinishedMatchComesBackAfterARestart() {
        val config = VersusConfig(VersusRule.TIMER, 180, Difficulty.HARD)
        val (vm, storage) = launchVersus(config, size = 5)
        vm.play()
        play(vm, 6)
        letPass(20)
        vm.setForeground(false) // the app is closed
        val playerScore = vm.score
        val botScore = vm.opponentScore
        val botMoves = vm.match!!.bot.moves
        val timeLeft = vm.displayTimeMs
        assertTrue(botMoves > 0)
        storage.flush()

        val again = GameViewModel(app, Storage(app), SystemClock::uptimeMillis, Runnable::run, audio, vibrations)
        assertEquals(Screen.GAME, again.screen)
        val match = again.match!!
        assertEquals(config, match.config)
        assertEquals(5, match.player.size)
        assertEquals(playerScore, again.score)
        assertEquals(botScore, again.opponentScore)
        assertEquals(botMoves, match.bot.moves)
        assertEquals(timeLeft, again.displayTimeMs)
        assertTrue(again.matchStarted)

        again.setForeground(true)
        letPass(5)
        assertTrue("the opponent carries on", match.bot.moves > botMoves)
    }

    @Test fun continueIsOfferedOnlyForTheSameSetup() {
        val (vm, _) = launchVersus(easyRace)
        assertFalse(vm.canContinue())
        vm.play()
        play(vm, 2)
        val match = vm.match
        vm.showMenu()
        assertTrue(vm.canContinue())
        vm.selectVersus(easyRace.with(difficulty = Difficulty.HARD))
        assertFalse("a different difficulty is a different match", vm.canContinue())
        vm.selectGrid(5)
        vm.selectVersus(easyRace)
        assertFalse(vm.canContinue())
        vm.selectGrid(4)
        assertTrue(vm.canContinue())
        vm.play()
        assertEquals(2, vm.moves)
        assertNotSame("it is a reloaded copy of the same match", match, vm.match)
    }

    @Test fun abandoningAMatchIsNotCountedAndStartsAFreshOne() {
        val (vm, _) = launchVersus(easyRace)
        vm.play()
        play(vm, 3)
        letPass(5)
        val old = vm.match
        vm.newGame()
        assertNotSame(old, vm.match)
        assertEquals(0, vm.moves)
        assertEquals(0L, vm.opponentScore)
        assertFalse(vm.matchStarted)
        assertEquals(0, vm.versusRecord.played)
        letPass(10)
        assertEquals("a stale answer from the old match must not move the new opponent", 0, vm.match!!.bot.moves)
    }

    @Test fun versusLeavesSinglePlayerSavesAndScoresAlone() {
        val (vm, storage) = launch()
        vm.play()
        play(vm, 9)
        vm.showMenu()
        vm.selectMode(Mode.VERSUS)
        vm.play()
        play(vm, 2)
        vm.showMenu()
        vm.selectMode(Mode.CLASSIC)
        assertTrue(vm.canContinue())
        vm.play()
        assertEquals(9, vm.moves)
        assertNull(vm.match)
        assertTrue(storage.scores(classic4).isEmpty)
    }

    // ---- Audio and settings ------------------------------------------------------------

    @Test fun musicFollowsTheScreenAndTheMode() {
        val (vm, _) = launch()
        assertEquals(MusicTrack.MENU, audio.track)
        vm.play()
        assertEquals(MusicTrack.GAME, audio.track)
        vm.showMenu()
        assertEquals(MusicTrack.MENU, audio.track)
        vm.selectRules(GameRules.ZEN)
        vm.play()
        assertEquals("Zen has its own, calmer track", MusicTrack.ZEN, audio.track)
        vm.showScores()
        assertEquals(MusicTrack.ZEN, audio.track) // unchanged until the menu is shown
        vm.showMenu()
        vm.selectMode(Mode.VERSUS)
        vm.play()
        assertEquals(MusicTrack.GAME, audio.track)
    }

    @Test fun aMoveSlidesThenPlaysTheMergeForTheBiggestNewTile() {
        val (vm, _) = launch()
        vm.play()
        audio.effects.clear()
        val g = vm.game!!
        // Play until a move merges something, checking each move's sounds.
        var checkedMerge = false
        var checkedPlain = false
        var i = 0
        while (!(checkedMerge && checkedPlain) && !g.isOver) {
            audio.effects.clear(); audio.merges.clear()
            if (g.move(Direction.entries[i++ % 4]) < 0) continue
            vm.onMoved()
            assertEquals(listOf(Sfx.SLIDE), audio.effects)
            assertTrue("the merge waits for the tiles to arrive", audio.merges.isEmpty())
            looper.idleFor(Duration.ofMillis(200))
            var biggest = 0
            for (c in 0 until g.cellCount) if (g.lastTrace.merged[c]) biggest = maxOf(biggest, g.exponentAt(c))
            if (biggest > 0) {
                assertEquals(listOf(biggest), audio.merges)
                assertEquals(listOf(Sfx.SLIDE), audio.effects)
                checkedMerge = true
            } else {
                assertTrue(audio.merges.isEmpty())
                assertEquals(listOf(Sfx.SLIDE, Sfx.SPAWN), audio.effects)
                checkedPlain = true
            }
        }
        assertTrue(checkedMerge && checkedPlain)
    }

    @Test fun gameOverAndWinAreAnnouncedOnceAndNotOnReopening() {
        val (vm, storage) = launch()
        vm.selectRules(GameRules.ZEN)
        vm.play()
        playToTheEnd(vm)
        assertEquals(1, audio.effects.count { it == Sfx.OVER })
        storage.flush()

        val second = RecordingAudio()
        val again = GameViewModel(app, Storage(app), { now }, Runnable::run, second, vibrations)
        assertEquals(SessionStatus.STUCK, again.status)
        assertTrue("reopening a stuck game is silent", second.effects.none { it == Sfx.OVER })
        again.undo()
        assertTrue(second.effects.contains(Sfx.UNDO))
    }

    @Test fun settingsAreAppliedAndRemembered() {
        val (vm, _) = launch()
        assertEquals("effects and music both start at 50", 0.5f, audio.effectsVolume)
        assertEquals(0.5f, audio.musicVolume)
        assertFalse(audio.muted)
        assertEquals(HapticLevel.NORMAL, vm.settings.haptics)

        vm.updateSettings(vm.settings.copy(effectsVolume = 0.25f, musicVolume = 0f, muted = true, haptics = HapticLevel.HIGH, theme = ThemeChoice.DARK))
        assertEquals(0.25f, audio.effectsVolume)
        assertEquals(0f, audio.musicVolume)
        assertTrue(audio.muted)

        val second = RecordingAudio()
        val again = GameViewModel(app, Storage(app), { now }, Runnable::run, second, vibrations)
        assertEquals(0.25f, again.settings.effectsVolume)
        assertEquals(0f, again.settings.musicVolume)
        assertTrue(again.settings.muted)
        assertEquals(HapticLevel.HIGH, again.settings.haptics)
        assertEquals(ThemeChoice.DARK, again.settings.theme)
        assertTrue("the saved volumes reach the audio at start-up", second.muted)
        assertEquals(0.25f, second.effectsVolume)
    }

    @Test fun audioGoesQuietInTheBackground() {
        val (vm, _) = launch()
        assertTrue(audio.inForeground)
        vm.setForeground(false)
        assertFalse(audio.inForeground)
        vm.setForeground(true)
        assertTrue(audio.inForeground)
    }

    @Test fun mergesVibrateAtTheChosenStrengthAndOnlyMerges() {
        val (vm, _) = launch()
        vm.play()
        val g = vm.game!!
        var i = 0
        var merges = 0
        while (merges < 3 && !g.isOver) {
            felt.clear()
            if (g.move(Direction.entries[i++ % 4]) < 0) continue
            vm.onMoved()
            looper.idleFor(Duration.ofMillis(200))
            var biggest = 0
            for (c in 0 until g.cellCount) if (g.lastTrace.merged[c]) biggest = maxOf(biggest, g.exponentAt(c))
            if (biggest > 0) {
                assertEquals(listOf(biggest to HapticLevel.NORMAL), felt)
                merges++
            } else {
                assertTrue("a move without a merge does not vibrate", felt.isEmpty())
            }
        }
        assertEquals(3, merges)
    }

    @Test fun choosingAVibrationStrengthLetsThePlayerFeelItAndIsRemembered() {
        val (vm, _) = launch()
        vm.setHaptics(HapticLevel.HIGH)
        assertEquals(HapticLevel.HIGH, felt.last().second)
        vm.setHaptics(HapticLevel.OFF)
        assertEquals(HapticLevel.OFF, vm.settings.haptics)
        val again = GameViewModel(app, Storage(app), { now }, Runnable::run, RecordingAudio(), vibrations)
        assertEquals(HapticLevel.OFF, again.settings.haptics)
    }

    @Test fun vibrationSwitchedOffInTheFirstAudioBuildStaysOff() {
        app.getSharedPreferences("settings", 0).edit().putBoolean("haptics", false).commit()
        val (vm, _) = launch()
        assertEquals(HapticLevel.OFF, vm.settings.haptics)
    }
}

package io.github.camilus123.game4096.game

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.camilus123.game4096.core.Direction
import io.github.camilus123.game4096.core.EndReason
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

    /** Fake time for the Time attack clock; tests move it forward by hand. */
    private var now = 1_000_000L

    private val classic4 = GameKey(4, GameRules.CLASSIC.id)

    private fun launch(): Pair<GameViewModel, Storage> {
        val storage = Storage(app)
        val vm = GameViewModel(app, storage) { now }
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
}

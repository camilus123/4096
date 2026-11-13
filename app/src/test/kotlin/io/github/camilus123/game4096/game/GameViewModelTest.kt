package io.github.camilus123.game4096.game

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.camilus123.game4096.core.Direction
import io.github.camilus123.game4096.core.Game
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

    private fun launch(): Pair<GameViewModel, Storage> {
        val storage = Storage(app)
        return GameViewModel(app, storage) to storage
    }

    private fun Game.cells(): IntArray = IntArray(cellCount).also { copyCellsInto(it) }

    /** Makes up to [count] moves the way the board view does: move, then tell the view model. */
    private fun play(vm: GameViewModel, count: Int) {
        val g = vm.game!!
        var made = 0
        var i = 0
        while (made < count && !g.isOver) {
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
        assertFalse(vm.hasSavedGame(4))
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
        assertTrue(again.hasSavedGame(4))
        assertFalse(again.hasSavedGame(5))
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
        assertEquals(GameStatus.OVER, vm.status)
        val score = vm.score
        assertTrue(score > 0)
        assertEquals(0, vm.finalRank)
        assertEquals(score, vm.best)
        storage.flush()

        val (again, storage2) = launch()
        assertEquals("a finished game is not reopened", Screen.MENU, again.screen)
        assertFalse(again.hasSavedGame(4))
        val table = storage2.scores(GameKey(4, GameKey.CLASSIC))
        assertEquals(1, table.entries.size)
        assertEquals(score, table.best)
        again.play()
        assertEquals(0L, again.score)
        assertEquals("the best score is shown from the first move", score, again.best)
    }

    @Test fun scoresAreKeptPerGrid() {
        val (vm, storage) = launch()
        vm.selectGrid(4); vm.play(); playToTheEnd(vm); vm.showMenu()
        val small = vm.scores(4, GameKey.CLASSIC).best
        vm.selectGrid(5); vm.play(); playToTheEnd(vm); vm.showMenu()
        storage.flush()

        val (_, fresh) = launch()
        assertEquals(small, fresh.scores(GameKey(4, GameKey.CLASSIC)).best)
        assertEquals(1, fresh.scores(GameKey(4, GameKey.CLASSIC)).entries.size)
        assertEquals(1, fresh.scores(GameKey(5, GameKey.CLASSIC)).entries.size)
        assertTrue(fresh.scores(GameKey(6, GameKey.CLASSIC)).isEmpty)
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
        assertEquals(1, storage.scores(GameKey(4, GameKey.CLASSIC)).entries.size)

        vm.newGame() // a game with no points is not worth a table row
        assertEquals(1, storage.scores(GameKey(4, GameKey.CLASSIC)).entries.size)
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
        assertFalse(vm.hasSavedGame(4))
        vm.play()
        assertNotNull(vm.game)
        assertEquals(0, vm.moves)
    }
}

package io.github.camilus123.game4096.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import io.github.camilus123.game4096.game.BoardView
import androidx.test.core.app.ApplicationProvider
import io.github.camilus123.game4096.MainActivity
import io.github.camilus123.game4096.core.Difficulty
import io.github.camilus123.game4096.core.Direction
import io.github.camilus123.game4096.core.Outcome
import io.github.camilus123.game4096.core.VersusConfig
import io.github.camilus123.game4096.core.VersusRule
import io.github.camilus123.game4096.core.GameRules
import io.github.camilus123.game4096.core.Mode
import io.github.camilus123.game4096.core.ScoreEntry
import io.github.camilus123.game4096.data.GameKey
import io.github.camilus123.game4096.data.Storage
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.Duration

/**
 * Starts the real activity and draws whole screens to app/build/screen-renders/, so layout
 * can be looked at without a phone. The only assertion is that something was drawn.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-420dpi")
class ScreenRenderTest {

    private fun capture(activity: MainActivity, name: String) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        val root = activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File("build/screen-renders").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val first = bitmap.getPixel(0, 0)
        var differs = false
        var y = 0
        while (y < bitmap.height && !differs) {
            var x = 0
            while (x < bitmap.width) {
                if (bitmap.getPixel(x, y) != first) { differs = true; break }
                x += 7
            }
            y += 7
        }
        assertTrue("screen $name is blank", differs)
    }

    private fun launchActivity(): MainActivity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

    /** See BoardViewTest: ambient motion and Robolectric's run-until-idle cannot be combined. */
    @Before fun stillAmbient() {
        BoardView.ambientMotionAllowed = false
    }

    // The activities are deliberately not destroyed here: after one is torn down, Compose in
    // the next test's activity stops recomposing under Robolectric.
    @After fun allowAmbientAgain() {
        BoardView.ambientMotionAllowed = true
    }

    /** The board the player swipes on: the interactive BoardView in the activity. */
    private fun playerBoard(activity: MainActivity): BoardView {
        fun find(v: View): BoardView? {
            if (v is BoardView && v.interactive) return v
            if (v is ViewGroup) for (c in 0 until v.childCount) find(v.getChildAt(c))?.let { return it }
            return null
        }
        // Compose may be between two layouts; give it a moment before giving up.
        repeat(20) {
            find(activity.window.decorView)?.let { return it }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        }
        val vm = androidx.lifecycle.ViewModelProvider(activity)[io.github.camilus123.game4096.game.GameViewModel::class.java]
        error("no board on screen: screen=${vm.screen} match=${vm.match != null} session=${vm.session != null} outcome=${vm.outcome}")
    }

    private fun storage() = Storage.get(ApplicationProvider.getApplicationContext())

    private fun seed() {
        Storage.resetForTests()
        val storage = storage()
        val day = 86_400_000L
        val t0 = 1_794_000_000_000L
        for ((i, s) in longArrayOf(20480, 15872, 9004, 7216, 3120).withIndex()) {
            storage.addScore(GameKey(4, "classic"), ScoreEntry(s, 11 - i, 900 - i * 150, t0 + i * day))
        }
        storage.addScore(GameKey(5, "classic"), ScoreEntry(61234, 12, 2100, t0))
        storage.addScore(GameKey(4, "zen"), ScoreEntry(31000, 11, 1500, t0))
        storage.addScore(GameKey(4, "time_180"), ScoreEntry(5120, 9, 240, t0 + day))
        storage.addScore(GameKey(4, "moves_100"), ScoreEntry(1408, 7, 100, t0 + 2 * day))
    }

    @Test @Config(qualifiers = "w411dp-h914dp-420dpi")
    fun lightScreens() = renderAll("light")

    @Test @Config(qualifiers = "w411dp-h914dp-night-420dpi")
    fun darkScreens() = renderAll("dark")

    /**
     * Slovenian words are longer than English ones and some phones are narrow: this is the
     * combination where labels get cut off. Renders the screens with the most text on them.
     */
    @Test @Config(qualifiers = "sl-w360dp-h800dp-420dpi")
    fun slovenianOnANarrowPhone() {
        seed()
        storage().addVersusResult(4, Difficulty.HARD, Outcome.BOT_WON)
        val activity = launchActivity()
        val vm = androidx.lifecycle.ViewModelProvider(activity)[io.github.camilus123.game4096.game.GameViewModel::class.java]
        vm.selectMode(Mode.VERSUS)
        vm.selectVersus(VersusConfig(VersusRule.LAST_STANDING, 0, Difficulty.HARD))
        capture(activity, "sl-menu-versus")
        vm.play()
        capture(activity, "sl-versus")
        vm.showMenu()
        vm.selectRules(GameRules(Mode.LIMITED_MOVES, 100))
        capture(activity, "sl-menu-moves")
        vm.play()
        capture(activity, "sl-game-moves")
        vm.showMenu()
        vm.selectRules(GameRules.ZEN)
        vm.play()
        capture(activity, "sl-zen")
        vm.showMenu()
        vm.showSettings()
        capture(activity, "sl-settings")
        vm.showMenu()
        vm.showScores()
        capture(activity, "sl-scores")
    }

    private fun renderAll(prefix: String) {
        seed()
        val activity = launchActivity()
        capture(activity, "$prefix-menu")
        val vm = androidx.lifecycle.ViewModelProvider(activity)[io.github.camilus123.game4096.game.GameViewModel::class.java]
        vm.showScores()
        capture(activity, "$prefix-scores")
        vm.showMenu()
        vm.play()
        capture(activity, "$prefix-game")
        vm.showMenu()

        vm.selectRules(GameRules(Mode.TIME_ATTACK, 180))
        capture(activity, "$prefix-menu-time")
        vm.play()
        capture(activity, "$prefix-game-time")
        vm.showMenu()

        vm.selectGrid(6)
        vm.selectRules(GameRules(Mode.LIMITED_MOVES, 50))
        vm.play()
        val g = vm.game!!
        var i = 0
        while (vm.canAcceptMove()) {
            if (g.move(Direction.entries[i++ % 4]) >= 0) vm.onMoved()
        }
        capture(activity, "$prefix-over-moves")
        vm.showMenu()

        vm.selectGrid(4)
        vm.selectRules(GameRules.ZEN)
        vm.play()
        val z = vm.game!!
        while (vm.canAcceptMove()) {
            if (z.move(Direction.entries[i++ % 4]) >= 0) vm.onMoved()
        }
        capture(activity, "$prefix-zen-stuck")
        vm.undo()
        capture(activity, "$prefix-zen")
        vm.showMenu()

        // Versus: the setup, a match a little way in, and a result.
        storage().addVersusResult(4, Difficulty.EASY, Outcome.PLAYER_WON)
        storage().addVersusResult(4, Difficulty.EASY, Outcome.BOT_WON)
        vm.selectMode(Mode.VERSUS)
        vm.selectVersus(VersusConfig(VersusRule.TIMER, 180, Difficulty.EASY))
        capture(activity, "$prefix-menu-versus")
        vm.selectGrid(5)
        vm.play()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        val keys = intArrayOf(
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN,
        )
        repeat(60) { k ->
            // Swipe on the real board view, as a player would, and let the opponent answer.
            playerBoard(activity).onKeyDown(keys[k % 4], KeyEvent(KeyEvent.ACTION_DOWN, keys[k % 4]))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
            Thread.sleep(3) // the opponent thinks on its own thread
        }
        capture(activity, "$prefix-versus")
        vm.showMenu()

        vm.selectGrid(4)
        vm.selectVersus(VersusConfig(VersusRule.RACE, 256, Difficulty.EASY))
        vm.play()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        var waited = 0
        while (vm.outcome == Outcome.UNDECIDED && waited < 4000) {
            if (waited < 12) playerBoard(activity).onKeyDown(keys[waited % 4], KeyEvent(KeyEvent.ACTION_DOWN, keys[waited % 4]))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            Thread.sleep(2)
            waited++
        }
        assertTrue("the match never ended", vm.outcome != Outcome.UNDECIDED)
        capture(activity, "$prefix-versus-result")
        vm.showMenu()
        vm.showScores()
        capture(activity, "$prefix-scores-versus")
        vm.showMenu()
        capture(activity, "$prefix-menu-final")
        vm.showSettings()
        capture(activity, "$prefix-settings")
        vm.updateSettings(vm.settings.copy(showFrameStats = true, quality = io.github.camilus123.game4096.data.QualityChoice.HIGH))
        vm.showBenchmark()
        capture(activity, "$prefix-benchmark")
        vm.showMenu()
        vm.selectMode(Mode.CLASSIC)
        vm.selectGrid(4)
        vm.play()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        repeat(30) { k ->
            playerBoard(activity).onKeyDown(keys[k % 4], KeyEvent(KeyEvent.ACTION_DOWN, keys[k % 4]))
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        capture(activity, "$prefix-game-high-stats")
    }
}

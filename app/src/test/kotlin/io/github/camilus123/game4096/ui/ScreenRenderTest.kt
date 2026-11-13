package io.github.camilus123.game4096.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.camilus123.game4096.MainActivity
import io.github.camilus123.game4096.core.ScoreEntry
import io.github.camilus123.game4096.data.GameKey
import io.github.camilus123.game4096.data.Storage
import org.junit.Assert.assertTrue
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

    private fun seed() {
        Storage.resetForTests()
        val storage = Storage.get(ApplicationProvider.getApplicationContext())
        val day = 86_400_000L
        val t0 = 1_794_000_000_000L
        for ((i, s) in longArrayOf(20480, 15872, 9004, 7216, 3120).withIndex()) {
            storage.addScore(GameKey(4, GameKey.CLASSIC), ScoreEntry(s, 11 - i, 900 - i * 150, t0 + i * day))
        }
        storage.addScore(GameKey(5, GameKey.CLASSIC), ScoreEntry(61234, 12, 2100, t0))
    }

    @Test @Config(qualifiers = "w411dp-h914dp-420dpi")
    fun lightScreens() = renderAll("light")

    @Test @Config(qualifiers = "w411dp-h914dp-night-420dpi")
    fun darkScreens() = renderAll("dark")

    private fun renderAll(prefix: String) {
        seed()
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        capture(activity, "$prefix-menu")
        val vm = androidx.lifecycle.ViewModelProvider(activity)[io.github.camilus123.game4096.game.GameViewModel::class.java]
        vm.showScores()
        capture(activity, "$prefix-scores")
        vm.showMenu()
        vm.play()
        capture(activity, "$prefix-game")
    }
}

package io.github.camilus123.game4096.game

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import io.github.camilus123.game4096.core.Game
import io.github.camilus123.game4096.core.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
 * Runs the real BoardView on the JVM with Robolectric's native graphics, so the drawing code,
 * the swipe recogniser and the animation clock are exercised without a phone.
 * Rendered boards are written to app/build/board-renders/ for a human to look at.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class BoardViewTest {

    private val side = 1080

    private fun newView(): BoardView {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = BoardView(activity)
        activity.setContentView(view)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(side, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(side, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, side, side)
        return view
    }

    private fun settle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

    private fun render(view: View, name: String): Bitmap {
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/board-renders").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    private fun gameOf(size: Int, vararg exponents: Int): Game =
        Game.restore(GameState(size, exponents, 0L, 0, 7L))

    /** Colour just inside the top-left corner of a cell, clear of the label. */
    private fun cellColor(bitmap: Bitmap, size: Int, row: Int, col: Int): Int {
        val gap = side * 0.11f / size
        val cell = (side - gap * (size + 1)) / size
        val x = gap + col * (cell + gap) + cell * 0.12f
        val y = gap + row * (cell + gap) + cell * 0.12f
        return bitmap.getPixel(x.toInt(), y.toInt())
    }

    @Test fun drawsEveryTileInItsColour() {
        for (dark in listOf(false, true)) {
            val palette = if (dark) BoardPalette.DARK else BoardPalette.LIGHT
            for (size in intArrayOf(4, 5, 6)) {
                val cells = IntArray(size * size) { if (it < 17) it else 0 }
                val view = newView()
                view.palette = palette
                view.bind(gameOf(size, *cells))
                settle()
                val bitmap = render(view, "${if (dark) "dark" else "light"}-${size}x$size")
                for (i in cells.indices) {
                    val expected = if (cells[i] == 0) palette.emptyCell else palette.tile(cells[i])
                    assertEquals("cell $i on ${size}x$size", expected, cellColor(bitmap, size, i / size, i % size))
                }
            }
        }
    }

    @Test fun keyPressMovesTilesAndReportsTheMove() {
        val game = gameOf(4, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val view = newView()
        var reported = 0
        view.onMoved = { reported++ }
        view.bind(game)
        settle()

        view.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(3, game.exponentAt(0))
        assertEquals(1, game.moves)
        assertEquals(1, reported)

        settle()
        val bitmap = render(view, "after-left")
        assertEquals(BoardPalette.LIGHT.tile(3), cellColor(bitmap, 4, 0, 0))
        assertNotEquals(BoardPalette.LIGHT.tile(3), cellColor(bitmap, 4, 0, 3))
    }

    @Test fun swipeIsRecognisedBeforeTheFingerLifts() {
        val game = gameOf(4, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val view = newView()
        view.bind(game)
        settle()

        val t = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 600f, 500f, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(t, t + 8, MotionEvent.ACTION_MOVE, 595f, 500f, 0))
        assertEquals("a tiny movement is not a swipe", 0, game.moves)
        view.dispatchTouchEvent(MotionEvent.obtain(t, t + 16, MotionEvent.ACTION_MOVE, 400f, 520f, 0))
        assertEquals("the swipe fires during the drag", 1, game.moves)
        assertEquals(3, game.exponentAt(0))
        view.dispatchTouchEvent(MotionEvent.obtain(t, t + 24, MotionEvent.ACTION_UP, 380f, 520f, 0))
        assertEquals(1, game.moves)
    }

    @Test fun diagonalDragIsIgnored() {
        val game = gameOf(4, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val view = newView()
        view.bind(game)
        settle()
        val t = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 300f, 300f, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(t, t + 16, MotionEvent.ACTION_MOVE, 500f, 500f, 0))
        assertEquals(0, game.moves)
    }

    @Test fun swipesDuringTheSlideAreQueuedNotDropped() {
        val game = gameOf(4, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val view = newView()
        view.bind(game)
        settle()

        fun press(code: Int) = view.onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals(1, game.moves)
        press(KeyEvent.KEYCODE_DPAD_DOWN) // arrives while the first slide is still running
        assertEquals("the second swipe waits for the slide", 1, game.moves)
        settle()
        assertEquals("the queued swipe was applied", 2, game.moves)
        assertEquals(3, game.exponentAt(3, 0))
    }

    @Test fun inputIsIgnoredWhenDisabled() {
        val game = gameOf(4, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val view = newView()
        view.bind(game)
        settle()
        view.inputEnabled = false
        view.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(0, game.moves)
    }

    @Test fun aRestingBoardRequestsNoFrames() {
        val view = newView()
        view.bind(gameOf(4, 1, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
        settle()
        val looper = shadowOf(Looper.getMainLooper())
        assertTrue("nothing is scheduled once the animation has finished", looper.isIdle)
        assertFalse(view.isDirty)
    }
}

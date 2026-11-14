package io.github.camilus123.game4096.game

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import io.github.camilus123.game4096.core.Direction
import io.github.camilus123.game4096.core.Game
import io.github.camilus123.game4096.core.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
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

    private val controllers = ArrayList<ActivityController<Activity>>()

    /**
     * High quality's ambient motion never stops, and Robolectric runs the main thread until
     * nothing is left to do, so the two cannot be combined. These tests run with the motion
     * switched off (the glow and lights are drawn still) and check the switch separately.
     */
    @Before fun stillAmbient() {
        BoardView.ambientMotionAllowed = false
    }

    @After fun destroyActivities() {
        for (c in controllers) c.pause().stop().destroy()
        controllers.clear()
        BoardView.ambientMotionAllowed = true
    }

    private fun newView(): BoardView {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        controllers.add(controller)
        val activity = controller.get()
        val view = BoardView(activity)
        activity.setContentView(view)
        return view
    }

    /** The window lays the view out at the (small) default screen size; force the size under test. */
    private fun layout(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(side, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(side, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, side, side)
    }

    private fun settle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

    private fun render(view: View, name: String): Bitmap {
        layout(view)
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
        assertFalse(view.isAnimating)
    }

    // ---- Quality levels ----------------------------------------------------------------

    private val sample = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 0, 0, 3, 3)

    @Test fun everyQualityLevelDrawsTheSameTilesInTheSamePlaces() {
        for (dark in listOf(false, true)) {
            val palette = if (dark) BoardPalette.DARK else BoardPalette.LIGHT
            for (quality in Quality.entries) {
                val view = newView()
                view.palette = palette
                view.quality = quality
                view.bind(gameOf(4, *sample))
                settle()
                val bitmap = render(view, "quality-${quality.name.lowercase()}-${if (dark) "dark" else "light"}")
                for (i in sample.indices) {
                    if (sample[i] != 0) assertEquals("cell $i at $quality", palette.tile(sample[i]), cellColor(bitmap, 4, i / 4, i % 4))
                }
            }
        }
    }

    /**
     * Robolectric plays a whole animation inside one turn of the main thread, so its middle
     * cannot be seen from outside. These tests hand the view its frame timestamps directly.
     */
    private fun frameAt(view: BoardView, millis: Long) = view.doFrame(1_000_000_000L + millis * 1_000_000L)

    @Test fun animationsTakeTheSameTimeAtEveryQualityLevel() {
        for (quality in Quality.entries) {
            val game = gameOf(4, 0, 0, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
            val view = newView()
            view.quality = quality
            view.bind(game)
            settle()
            view.swipe(Direction.LEFT)
            assertEquals("the move itself is instant", 3, game.exponentAt(0))
            frameAt(view, 0)
            frameAt(view, 100)
            assertTrue("sliding at 100 ms on $quality", view.isAnimating)
            frameAt(view, 250)
            assertTrue("popping at 250 ms on $quality", view.isAnimating)
            frameAt(view, 254)
            assertFalse("settled at 254 ms (110 slide + 143 pop) on $quality", view.isAnimating)
        }
    }

    @Test fun lowAndMediumDrawNothingAtRestButHighKeepsItsAmbientMotion() {
        for (quality in Quality.entries) {
            val view = newView()
            view.quality = quality
            view.bind(gameOf(4, 0, 0, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
            settle()
            view.swipe(Direction.LEFT) // a merge: rings at Medium, sparks at High
            settle()
            settle()
            assertFalse(view.isAnimating)
            assertFalse("with ambient motion off, nothing moves at rest on $quality", view.wantsFrames)
            // With the motion allowed, as in the app, only High keeps going.
            BoardView.ambientMotionAllowed = true
            assertEquals("at rest on $quality", quality == Quality.HIGH, view.wantsFrames)
            BoardView.ambientMotionAllowed = false
        }
    }

    @Test fun theOpponentsBoardNeverRunsAmbientMotion() {
        val view = newView()
        view.interactive = false
        view.quality = Quality.HIGH
        view.bind(gameOf(4, 7, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
        settle()
        BoardView.ambientMotionAllowed = true
        assertFalse(view.wantsFrames)
        BoardView.ambientMotionAllowed = false
    }

    @Test fun mergeSparksAppearAtHighAndBurnOut() {
        val game = gameOf(4, 0, 0, 5, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val view = newView()
        view.quality = Quality.HIGH
        view.bind(game)
        settle()
        view.swipe(Direction.LEFT)
        frameAt(view, 0)
        frameAt(view, 60)
        assertFalse("no sparks while the tiles are still sliding", view.wantsFrames && !view.isAnimating)
        frameAt(view, 115) // the slide has just ended: the burst
        for (t in 130..330 step 16) frameAt(view, t.toLong())
        assertFalse(view.isAnimating)
        assertTrue("sparks are still flying after the tiles settle", view.wantsFrames)
        val during = render(view, "quality-high-sparks")
        settle()
        settle()
        assertFalse("the sparks burn out and the board comes to rest", view.wantsFrames)
        val after = render(view, "quality-high-after-sparks")
        // The two pictures show the same settled board; whatever differs between them is sparks.
        var differing = 0
        for (y in 0 until side step 2) {
            for (x in 0 until side step 2) {
                if (during.getPixel(x, y) != after.getPixel(x, y)) differing++
            }
        }
        assertTrue("sparks were drawn ($differing pixels differ)", differing > 20)
    }

    @Test fun theSixtyLimitDrawsEveryOtherRefreshOnAFastScreenAndEveryRefreshOnASlowOne() {
        fun framesHandled(periodMicros: Long, limit: Boolean): Int {
            val game = gameOf(4, 0, 0, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
            val view = newView()
            view.limitTo60 = limit
            view.bind(game)
            settle()
            view.swipe(Direction.LEFT)
            // Feed refreshes for 200 ms (the animation lasts 253) and count the ones that asked for a redraw.
            var handled = 0
            var t = 0L
            val base = SystemClock.uptimeNanos() + 1_000_000_000L // clearly after any frame the view has seen
            while (t <= 200_000L) {
                view.doFrame(base + t * 1000L)
                if (view.isDirty) handled++
                // Robolectric never draws by itself; drawing clears the dirty flag as a real frame would.
                render(view, "limit-scratch")
                t += periodMicros
            }
            assertTrue(view.isAnimating)
            return handled
        }
        val full120 = framesHandled(8_333, limit = false)
        val limited120 = framesHandled(8_333, limit = true)
        val limited60 = framesHandled(16_667, limit = true)
        val full60 = framesHandled(16_667, limit = false)
        assertEquals(25, full120)
        assertTrue("half the frames on a 120 Hz screen, drew $limited120 of $full120", limited120 in 12..13)
        assertEquals("a 60 Hz screen is not held back", full60, limited60)
        assertEquals(12, full60)
    }

    @Test fun theBoardCountsItsOwnDraws() {
        val view = newView()
        view.bind(gameOf(4, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
        settle()
        val before = view.drawCount
        render(view, "count-scratch")
        render(view, "count-scratch")
        assertEquals(before + 2, view.drawCount)
    }

    @Test fun driftingLightsAreInvisibleAtTheEdgeWhereTheyWrapAround() {
        val w = 1000f
        val h = 1600f
        val r = 100f
        // At any edge, which is where a light jumps to the opposite side, it cannot be seen.
        for (pos in listOf(0f to 800f, 1000f to 800f, 500f to 0f, 500f to 1600f, 0f to 0f)) {
            assertEquals(0f, BoardView.edgeFade(pos.first, pos.second, w, h, r), 0f)
        }
        assertEquals("fully visible well inside", 1f, BoardView.edgeFade(500f, 800f, w, h, r), 0f)
        assertEquals(1f, BoardView.edgeFade(150f, 800f, w, h, r), 0f)
        // In between it changes gradually, with no jump from one frame to the next.
        var previous = 0f
        var x = 0f
        while (x <= 150f) {
            val f = BoardView.edgeFade(x, 800f, w, h, r)
            assertTrue("fade never steps back", f >= previous)
            assertTrue("fade moves in small steps, jumped ${f - previous} at $x", f - previous < 0.02f)
            previous = f
            x += 1f
        }
    }
}
